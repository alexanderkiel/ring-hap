(ns ring-hap.core-test
  (:require [clojure.test :refer :all]
            [ring-hap.core :refer :all]))

(deftest transit-format-test
  (are [media-type format] (= format (transit-format media-type))
    nil nil
    1 nil
    "text/plain" nil
    "application/xml" nil
    "application/json" nil
    "application/transit+json" :json
    "application/transit+msgpack" :msgpack))

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
