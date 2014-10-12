

(true? (list (first (dz/descendants d))))

(codes nil)


(count (mapcat codes (dz/descendants d)))

(println (count d))

(println (interpose "\n" (->> (dz/descendants d)
                              (filter #(= :code (:tag (zip/node %))))
                              (map (comp :code :attrs zip/node))
                              )))

(defn template d t)
