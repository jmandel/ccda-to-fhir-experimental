

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


