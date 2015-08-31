(ns ring-hap.core
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.string :as str]
            [ring.util.codec :as codec]
            [ring.util.request :as req]
            [cognitect.transit :as transit]
            [transit-schema.core :as ts])
  (:import [java.io ByteArrayOutputStream]
           [java.net URI]))

(set! *warn-on-reflection* true)

(def ^:private default-read-handlers
  (-> ts/read-handlers
      (assoc "r" (transit/read-handler #(URI/create %)))))

(defn transit-format
  "Determines the format of a media type.

  Extra is the part after the semicolon.

  application/transit+json         -> :json
  application/transit+json;verbose -> :json-verbose
  application/transit+msgpack      -> :msgpack"
  ([media-type] (transit-format media-type nil))
  ([media-type extra]
   (when (string? media-type)
     (let [[type subtype] (str/split media-type #"/")]
       (when (= "application" type)
         (case subtype
           ("transit+json" "json" "*")
           (if (and (string? extra) (.contains ^String extra "verbose"))
             :json-verbose
             :json)
           "transit+msgpack"
           :msgpack
           nil))))))

(defn parse-body [read-opts request]
  (if-let [body (:body request)]
    (if-let [format (-> (req/content-type request) (transit-format))]
      (->> (transit/reader body format read-opts)
           (transit/read)))))

(defn assoc-params-from-body [read-opts request]
  (if-let [parsed-body (parse-body read-opts request)]
    (update request :params #(merge % parsed-body))
    request))

(defn do-parse-body [read-opts request]
  (assoc request :body (parse-body read-opts request)))

(defn parse-params [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn transit-read-str [read-opts ^String s]
  (-> (.getBytes s "utf-8")
      (io/input-stream)
      (transit/reader :json read-opts)
      (transit/read)))

(defn transit-read-str-ex [read-opts s]
  (try
    (transit-read-str read-opts s)
    (catch Exception e
      (throw (ex-info (str "Parse error on: " s)
                      {:type :parse-error :input s} e)))))

(defn decode-params
  "Decodes already parsed params by transforming keys to keywords and reading
  values as Transit."
  [params read-opts]
  (reduce-kv
    (fn [r k v]
      (assoc r (keyword k) (transit-read-str-ex read-opts v)))
    {}
    params))

(defn query-params [request read-opts encoding]
  (some-> (:query-string request)
          (parse-params encoding)
          (decode-params read-opts)))

(defn assoc-params-from-query-params [request read-opts encoding]
  (if-let [params (query-params request read-opts encoding)]
    (assoc request :params params)
    request))

(defn hap-request
  "Adds parameters from the query string and the request body to the request
  map. See: wrap-hap."
  {:arglists '([request] [request options])}
  [request & [opts]]
  (let [read-opts {:handlers (:read-handlers opts)}
        encoding (or (:encoding opts)
                     (req/character-encoding request)
                     "UTF-8")
        request (assoc-params-from-query-params request read-opts encoding)]
    (condp = (:request-method request)

      :post (assoc-params-from-body read-opts request)
      :put (do-parse-body read-opts request)

      request)))

(defn error-body [msg {:keys [up-href]}]
  (let [body {:data {:message msg}}]
    (if up-href
      (assoc-in body [:links :up :href] up-href)
      body)))

(defn wrap-transit-request [handler opts]
  (let [opts (update opts :read-handlers #(-> (merge default-read-handlers %)
                                              (transit/read-handler-map)))]
    (fn [req]
      (try
        (handler (hap-request req opts))
        (catch Exception e
          ;; Handler doesn't throw because it's wrapped in wrap-exception
          {:status 400
           :body (error-body (str "Bad Request: " (.getMessage e)) opts)})))))

(defn- write-transit [format write-opts o]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out format write-opts) o)
    (io/input-stream (.toByteArray out))))

(defn content-type [format]
  (case format
    (:json :json-verbose)
    "application/transit+json"
    :msgpack
    "application/transit+msgpack"))

(defn hap-response [format write-opts resp]
  (-> (update resp :body #(when % (write-transit format write-opts %)))
      (assoc-in [:headers "Content-Type"] (content-type format))))

(defn accept [request]
  (if-let [type (get-in request [:headers "accept"])]
    (rest (re-find #"^(.*?)(?:;|$)(.+)?$" type))))

(defn wrap-transit-response [handler opts]
  (let [write-opts
        {:handlers (-> (merge ts/write-handlers (:write-handlers opts))
                       (transit/write-handler-map))}]
    (fn [req]
      (if-let [format (some->> (accept req) (apply transit-format))]
        (hap-response format write-opts (handler req))
        {:status 406}))))

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
        (flush)
        {:status 500
         :body (error-body (.getMessage t) opts)}))))

(defn wrap-hap
  "Middleware to handle all aspects of the Hypermedia Application Protocol.

  Accepts the following opts:

  :encoding - encoding to use for url-decoding. If not specified, uses
              the request character encoding, or \"UTF-8\" if no request
              character encoding is set

  :up-href - an href for :up links in error messages. Up link will be skipped
             if not set

  :read-handlers - a map of additional Transit read handlers

  :write-handlers - a map of additional Transit write handlers"
  {:arglists '([handler] [handler opts])}
  [handler & [opts]]
  (-> handler
      (wrap-not-found opts)
      (wrap-exception opts)
      (wrap-transit-request opts)
      (wrap-transit-response opts)))
