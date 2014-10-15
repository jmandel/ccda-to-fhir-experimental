(ns ccda-to-fhir.test
  ( :use [ccda-to-fhir.core])
  ( :require
[clojure.data.zip :as dz]
    [clojure.zip :as zip]
    [clojure.data.xml :as xml]
    [clojure.data.zip.xml :as x] 
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [clojure.pprint :as pprint])
  )



(true? (list (first (dz/descendants d))))

(codes nil)


(count (mapcat codes (dz/descendants d)))

(println (count d))

(println (interpose "\n" (->> (dz/descendants d)
                              (filter #(= :code (:tag (zip/node %))))
                              (map (comp :code :attrs zip/node))
                              )))

(defn template d t)


(to-fhir-dt "string" "test")

(fhir/datatypes-for-path "CodeableConcept.text")

(def ts (->> (first examples/parsed-files) -all-nodes (mapcat #(x/xml-> % :code ))))


(def ff (ccda-to-fhir.core/pval))
(time (def x (doall (ccda-to-fhir.core/pval))))
(type x)
(count x)
(type ( first x))
(first x)
(map realized? x)
(count x)

(type (first (-all-nodes (first examples/parsed-files))))


  (time (def dd (doall (for [n (range 10)]
                         (doall (follow-path  (->> (first examples/parsed-files) -all-nodes) [(x/attr :displayName)] ))))))
(println dd)
(map realized? dd)

(map count dd)
(type (first dd ))
(realized? (first dd))
(first (first dd))
(count dd)
(count (nth dd 300))
(nth dd 300)
(println (map count dd))



(time (pprint/pprint 
       (fill-in-template nil (first examples/parsed-files) {} (template/definitions  :ccda-sections) )))

(defn cval [] (let  [f1 (first examples/parsed-files)
                     ts (->> f1 -all-nodes (mapcat #(x/xml-> % :value )))]
                (println "Got some ts " (count ts))
                (def cc ts)))
(time ( cval))
(count cc)

(count  (follow-path (map auto cc) [(x/attr= :xsi/type "CD")]))

(time (def x (cval)))


(pprint/pprint  (let  [f1 (first examples/parsed-files)
                ts (->> f1 -all-nodes (mapcat #(x/xml-> % :value [(x/attr= :xsi/type "CD")]  )))]
           (println "Got some values " (count ts))
           (zip/node (first ts))
           ))



