(ns ccda-to-fhir.fhir
  ( :require
    [clojure.data.zip.xml :as x] 
    [clojure.data.json :as json]))

(def profiles
  (-> "/home/jmandel/smart/ccda-to-fhir/profiles-resources.json"
      clojure.java.io/reader json/read))

(def types
  (-> "/home/jmandel/smart/ccda-to-fhir/profiles-types.json"
      clojure.java.io/reader json/read))

(defn js-> [j & ks]
  (let [vectorized-input (if (vector? j) j [j])]
    (reduce (fn [results k]
              (when results
                (->> results
                     (map #(get % k))
                     (remove nil?)
                     (map (fn [x] ( if (vector? x) (flatten x) (vector x))))
                     (apply concat))))
            vectorized-input ks)))

(def fhir-paths
  (let [rpaths (js-> profiles "entry" "content" "structure" "snapshot" "element")
        tpaths (js-> types "entry" "content" "structure" "snapshot" "element")]
    (into {} (map (fn [x] [ (x "path") x]) (concat rpaths tpaths)))))

(defn first-component [s]
  (first  (clojure.string/split s #"\." 2)))

(def fhir-path-by-name
  (->> fhir-paths
       vals
       (remove #(nil? (% "name")))
       (reduce (fn [acc p]
                 (let [path (p "path")
                       name (p "name")]
                   (assoc acc
                     [(first-component path) name] path)))
               {})))

(defn kw-to-fhir [k] (subs (str k) 1))

(defn -datatypes-for-path [path]
  (let [elems     (fhir-paths path)
        types     (js-> elems "definition" "type")
        refs      (js-> elems "definition" "nameReference")
        resource  (first-component path)
        follow-ref-path #(fhir-path-by-name [resource %])]
    {:types (map #(% "code") types)
     :refs (reduce
            (fn [acc ref] (conj acc (follow-ref-path ref)))
            [] refs)}))


(println "loaded" (count fhir-paths) "paths")

(defn concrete-path-for-dt [path dt]
  (let [capitalized (apply str (clojure.string/upper-case (first dt)) (rest dt))]
    (clojure.string/replace path #"\[x\]" capitalized)))

(def path-to-datatypes
  ( ->> fhir-paths
        keys
        (mapcat
         (fn [p]
           (let [dts (get (-datatypes-for-path p) :types)]
             (concat [{:path p :types dts}]
                     (for [dt dts]
                       {:path (concrete-path-for-dt p dt) :types [dt]})))))
        (apply list)
        (reduce
         (fn [acc {:keys [path types]}]
           (assoc acc path types )
           ) {})))
