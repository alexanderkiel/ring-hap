(ns ring-hap.core
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.string :as str]
            [ring.util.codec :as codec]
            [ring.util.request :as req]
            [cognitect.transit :as transit]
            [outpace.schema-transit :as st])
  (:import [java.io ByteArrayOutputStream]
           [java.net URI]))

(def ^:private read-opts
  {:handlers
   (assoc st/read-handlers "r" (transit/read-handler #(URI/create %)))})

(def ^:private write-opts
  {:handlers st/write-handlers})

(defn transit-format [media-type]
  (when (string? media-type)
    (let [[type subtype] (str/split media-type #"/")]
      (when (= "application" type)
        ({"transit+json" :json
          "transit+msgpack" :msgpack} subtype)))))

(defn parse-body [request]
  (if-let [body (:body request)]
    (if-let [format (-> (req/content-type request) (transit-format))]
      (->> (transit/reader body format read-opts)
           (transit/read)))))

(defn assoc-params-from-body [request]
  (if-let [parsed-body (parse-body request)]
    (assoc request :params parsed-body)
    request))

(defn do-parse-body [request]
  (assoc request :body (parse-body request)))

(defn parse-params [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn transit-read-str [s]
  (-> (.getBytes s "utf-8")
      (io/input-stream)
      (transit/reader :json read-opts)
      (transit/read)))

(defn transit-read-str-ex [s]
  (try
    (transit-read-str s)
    (catch Exception e
      (throw (ex-info (str "Parse error on: " s)
                      {:type :parse-error :input s} e)))))

(defn decode-params
  "Decodes already parsed params by transforming keys to keywords and reading
  values as Transit."
  [params]
  (reduce-kv
    (fn [r k v]
      (assoc r (keyword k) (transit-read-str-ex v)))
    {}
    params))

(defn query-params [request encoding]
  (some-> (:query-string request)
          (parse-params encoding)
          (decode-params)))

(defn assoc-params-from-query-params [request encoding]
  (if-let [params (query-params request encoding)]
    (assoc request :params params)
    request))

(defn hap-request
  "Adds parameters from the query string and the request body to the request
  map. See: wrap-hap."
  {:arglists '([request] [request options])}
  [request & [opts]]
  (condp = (:request-method request)

    :get
    (let [encoding (or (:encoding opts)
                       (req/character-encoding request)
                       "UTF-8")]
      (assoc-params-from-query-params request encoding))

    :post (assoc-params-from-body request)
    :put (do-parse-body request)

    request))

(defn error-body [msg {:keys [up-href]}]
  (let [body {:error msg}]
    (if up-href
      (assoc-in body [:links :up :href] up-href)
      body)))

(defn wrap-transit-request [handler opts]
  (fn [req]
    (try
      (handler (hap-request req opts))
      (catch Exception e
        ;; Handler doesn't throw because it's wrapped in wrap-exception
        {:status 400
         :body (error-body (str "Bad Request: " (.getMessage e)) opts)}))))

(defn- write-transit [o]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json write-opts) o)
    (io/input-stream (.toByteArray out))))

(defn hap-response [resp]
  (-> (update resp :body write-transit)
      (assoc-in [:headers "Content-Type"] "application/transit+json")))

(defn wrap-transit-response [handler]
  (fn [req]
    (hap-response (handler req))))

(defn wrap-not-found [handler opts]
  (fn [req]
    (if-let [resp (handler req)]
      resp
      {:status 404
       :body (error-body "Not Found." opts)})))

(defn wrap-exception [handler opts]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (print-cause-trace t)
        {:status 500
         :body (error-body (.getMessage t) opts)}))))

(defn wrap-hap
  "Middleware to handle all aspects of the Hypermedia Application Protocol.

  Accepts the following opts:

  :encoding - encoding to use for url-decoding. If not specified, uses
              the request character encoding, or \"UTF-8\" if no request
              character encoding is set.

  :up-href - an href for :up links in error messages. Up link will be skipped
             if not set."
  {:arglists '([handler] [handler opts])}
  [handler & [opts]]
  (-> handler
      (wrap-not-found opts)
      (wrap-exception opts)
      (wrap-transit-request opts)
      (wrap-transit-response)))
