(ns spec.core
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.data.generators :as generators]
            [com.gfredericks.test.chuck.generators :as chuck]
            [clj-time.core :as t]
            [cheshire.core :as cheshire]
            [camel-snake-kebab.core :refer :all]
            [clj-time.coerce :refer [to-long]]
            [clj-time.types :refer [date-time?]])
  (:import [java.util UUID]
           [org.joda.time DateTime DateTimeZone LocalDate LocalDateTime]
           [org.joda.time.base BaseDateTime]))

;string-40
(def string-40-regex #"([a-zA-Z0-9]{1,40})")
(defn string-40 [] (chuck/string-from-regex string-40-regex))
(s/def ::string-40 (s/spec (s/and string? #(re-matches string-40-regex %)) :gen string-40))

;supplier-number
(def supplier-regex #"[A-Z]{1}[A-Z0-9]{1}[0-9]{3}")
(defn supplier-number? [x] (re-matches supplier-regex x))
(defn supplier-number [] (chuck/string-from-regex supplier-regex))
(s/def ::supplier-id (s/spec (s/and string? #(supplier-number? %)) :gen supplier-number))

;date
(s/def ::future (s/int-in (to-long (t/date-time (.getYear (t/now)) 12 31 23 59 59))
                          (to-long (t/date-time 2040 12 31 23 59 59))))

(s/def ::eta (s/with-gen date-time? #(gen/fmap
                                       (fn [t] (DateTime. t DateTimeZone/UTC))
                                       (s/gen ::future))))

;quantity
(s/def ::amount pos-int?)
(s/def ::unit #{:P :W})
(s/def ::quantity (s/keys :req-un [::amount ::unit]))

; line-item
(def pos-int-to-string (s/with-gen string? #(gen/fmap (fn [i] (str i)) (s/gen (s/int-in 1 1000)))))
(s/def ::item-id pos-int-to-string)
(s/def ::container-ids (s/coll-of pos-int-to-string :kind vector? :distinct true :min-count 1 :max-count 5))
(s/def ::gtin ::string-40)

;line-items
(s/def ::item (s/keys :req-un [::item-id
                               ::gtin
                               ::container-ids
                               ::quantity]))

(s/def ::items (s/coll-of ::item :min-count 3))

;asn
(s/def ::asn-id uuid?)
(s/def ::description ::string-40)
(s/def ::asn-type #{:external :internal :none})
(s/def ::deliverynote-id ::string-40)

(s/def ::asn (s/keys :req-un [::asn-id
                              ::eta
                              ::asn-type
                              ::supplier-id
                              ::items]
                     :opt-un [::deliverynote-id]))

(s/conform ::asn {
                  :asn-id      (gen/generate (gen/uuid))
                  :deliverynote-id (gen/generate (s/gen ::string-40))
                  :eta             (gen/generate (s/gen ::eta))
                  :asn-type        (gen/generate (s/gen ::asn-type))
                  :supplier-id     (gen/generate (s/gen ::supplier-id))
                  :items           (gen/sample (s/gen ::item) 3)})

(extend-protocol cheshire.generate/JSONable
  DateTime
  (to-json [dt gen] (cheshire.generate/write-string gen (str dt))))
(cheshire/generate-string (gen/generate (s/gen ::asn 1))
                          {:key-fn ->camelCaseString
                           :pretty true})