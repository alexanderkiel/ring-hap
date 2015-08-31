(ns ring-hap.core-test
  (:require [clojure.test :refer :all]
            [ring-hap.core :refer :all]))

(deftest transit-format-test
  (testing "media type without extra"
    (are [media-type format] (= format (transit-format media-type))
      nil nil
      1 nil
      "text/plain" nil
      "application/*" :json
      "application/xml" nil
      "application/json" :json
      "application/transit+json" :json
      "application/transit+msgpack" :msgpack))
  (testing "media type with extra"
    (are [media-type extra format] (= format (transit-format media-type extra))
      "application/transit+json" nil :json
      "application/transit+json" "verbose" :json-verbose
      "application/transit+json" "foo verbose foo" :json-verbose
      "application/transit+msgpack" nil :msgpack
      "application/transit+msgpack" "verbose" :msgpack)))

(deftest parse-params-test
  (are [s parsed] (= parsed (parse-params s "utf-8"))
    "a=b" {"a" "b"}
    "a=b&c=d" {"a" "b" "c" "d"}
    "a=\"b\"" {"a" "\"b\""}))

(deftest decode-params-test
  (testing "Positive Cases"
    (are [params parsed] (= parsed (decode-params params))
      {"a" "\"b\""} {:a "b"}
      {"a" "\"~:b\""} {:a :b}
      {"a" "[\"^ \", \"b\", \"c\"]"} {:a {"b" "c"}}))
  (testing "Parse Failures"
    (is (= :parse-error
           (try
             (decode-params {"a" "b"})
             (catch Exception e (:type (ex-data e))))))))

(defn- error-msg [resp]
  (-> resp :body :data :message))

(deftest wrap-transit-request-test
  (testing "Returns 400 (Bad Request) on invalid query param value"
    (let [req {:request-method :get
               :query-string "foo=bar"}
          resp ((wrap-transit-request identity {}) req)]
      (is (= 400 (:status resp)))
      (is (= "Bad Request: Parse error on: bar" (error-msg resp))))))

(deftest wrap-transit-response-test
  (testing "Returns 406 on missing Accept header."
    (is (= 406 (:status ((wrap-transit-response nil nil) {})))))

  (testing "Returns 406 on text/plain."
    (let [req {:headers {"accept" "text/plain"}}]
      (is (= 406 (:status ((wrap-transit-response nil nil) req))))))

  (testing "Returns a response on application/json."
    (let [req {:headers {"accept" "application/json"}}]
      (is (= 200 (:status ((wrap-transit-response #(assoc % :status 200) nil) req))))))

  (testing "Returns a response on application/*."
    (let [req {:headers {"accept" "application/*"}}]
      (is (= 200 (:status ((wrap-transit-response #(assoc % :status 200) nil) req)))))))

(deftest content-type-test
  (are [format type] (= type (content-type format))
    :json "application/transit+json"
    :json-verbose "application/transit+json"
    :msgpack "application/transit+msgpack"))
