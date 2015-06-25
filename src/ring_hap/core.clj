(ns ring-hap.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.util.codec :as codec]
            [ring.util.request :as req]
            [cognitect.transit :as transit]))

(defn transit-format [media-type]
  (when (string? media-type)
    (let [[type subtype] (str/split media-type #"/")]
      (when (= "application" type)
        ({"transit+json" :json
          "transit+msgpack" :msgpack} subtype)))))

(defn parse-body [request]
  (if-let [body (:body request)]
    (if-let [format (-> (req/content-type request) (transit-format))]
      (->> (transit/reader body format)
           (transit/read)))))

(defn assoc-params-from-body [request]
  (if-let [parsed-body (parse-body request)]
    (assoc request :params parsed-body)
    request))

(defn parse-params [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn transit-read-str [s]
  (-> (.getBytes s "utf-8")
      (io/input-stream)
      (transit/reader :json)
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
  (if (= :get (:request-method request))
    (let [encoding (or (:encoding opts)
                       (req/character-encoding request)
                       "UTF-8")]
      (assoc-params-from-query-params request encoding))
    (assoc-params-from-body request)))

(defn wrap-hap
  "Middleware to handle all aspects of the Transit Web Protocol.

  Accepts the following options:

  :encoding - encoding to use for url-decoding. If not specified, uses
              the request character encoding, or \"UTF-8\" if no request
              character encoding is set."
  {:arglists '([handler] [handler options])}
  [handler & [options]]
  (fn [request]
    (try
      (handler (hap-request request options))
      (catch Exception e
        (if (= :parse-error (:type (ex-data e)))
          {:status 400
           :body "Bad Request"}
          (throw e))))))
