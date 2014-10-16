(ns ccda-to-fhir.query
  ( :require
    [ccda-to-fhir.template :as template]
    [clojure.pprint :as pprint]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))


(def auto (partial dz/auto true))
(defn -all-nodes [d]
  (->> d dz/descendants (map auto)))

(defn -path-to-zipq [p]
  (println "handle quer " (type p) p)
  (let [components (clojure.string/split p #"/")]
    (->> components
         (remove empty?)
         (remove #(= "." %))
         (mapcat
          (fn [component]
            (let [attr-match (re-find #"(.*?)\[@(.*?)='(.*?)'\]" component)
                  attr (re-find #"@(.*)" component)
                  component (keyword component)]
              (println "Comp is " component)
              (cond
               attr-match (let [tag (keyword (attr-match 1))
                                tag (if (empty? (attr-match 1)) nil tag)
                                attr (keyword (clojure.string/replace (attr-match 2) #":" "/"))
                                value (attr-match 3)]
                            (remove nil? [tag [(x/attr= attr value)]]))
               attr [(x/attr (keyword (attr 1)))]
               :else [component])))))))

(defn restrict-query-to-template [q t]
  (let [tid (template/oids t)]
                                        ;(println "q type" (type q) tid)
    (if tid (conj (vec q) [:templateId (x/attr= :root tid)])
        q)))

(defn to-zipq [prop]
  (println "to zipq " (prop :xpath))
  (let [baseq  (-path-to-zipq (prop :xpath))
        template (prop :template)]
    (restrict-query-to-template baseq template)))

(defn follow-path [ccda-nodes path]
  (println "following from " (count ccda-nodes) "to " path)
  (println "following from " "to " path)
  (println (map :attrs (map zip/node ccda-nodes)))
  (let [ret
        (mapcat  #(apply x/xml-> % path) ccda-nodes)]
    (println "followed to get " (count ret))
    ret))

(defn follow-property [ccda-nodes prop]
  (follow-path ccda-nodes (to-zipq prop)))

(defn by-template [d t]
  (->> d -all-nodes
       (mapcat #(x/xml-> % [:templateId (x/attr= :root t)]))))
