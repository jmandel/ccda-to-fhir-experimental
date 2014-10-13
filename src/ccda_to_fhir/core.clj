(ns ccda-to-fhir.core
  ( :require
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))

(def auto (partial dz/auto true))
(def calls ( atom {:text 0 :follow 0}))

(defn -all-nodes [d]
  (->> d dz/descendants (map auto)))

(defn by-template [d t]
  (->> d -all-nodes
       (mapcat #(x/xml-> % [:templateId (x/attr= :root t)]))))


(defn parse-iso-time [t]
  (when t
    (if-let [pieces (re-find #"^(....)(..)?(..)?" t)]
      (apply str (interpose "-" (remove nil? (rest pieces)))))))

(defn null-value? [[k v]] (nil? v))

(defn parse-time-point [e]
  (fn [subpath]
    (let [subpath (or subpath identity)
          val (x/xml1-> e subpath (x/attr :value))
          null-flavor (x/xml1-> e subpath (x/attr :nullFlavor))
          as-map (->> {:iso (parse-iso-time val)
                       :null null-flavor}
                      (remove null-value?)
                      (into {}))]
      (when-not (empty? as-map) as-map))))

(defn parse-effective-time [e]
  (let [parts (map (parse-time-point e) [nil :low :high])]
    (zipmap [:point :low :high] parts)))

(defn fso-to-fhir [fso]
  
  (parse-effective-time (x/xml1-> (auto fso) :effectiveTime)))

(defn fsos-to-fhir [d]
  (let [tid (template/ids :functional-status-result-observation)
        targets (by-template d tid)]
    (map fso-to-fhir targets)))

(map fsos-to-fhir examples/parsed-files)



(defn map-to-fhir [{:keys [fhir-doc ccda-node mapping-context]}])

                                        ; for each prop in the template, call property-to-fhir
                                        ; then figure out how to slot that stuff into a return object

(defmulti to-fhir-dt (fn [dt ccda-node] dt))

(defmethod to-fhir-dt "string" [dt ccda-node]
  (if (= java.lang.String  (type ccda-node)) ccda-node 
      (do 

        (swap! calls update-in [:text] #(+ % 2))
        (let [valattr (x/xml1-> ccda-node (x/attr :value))
              text (x/xml1-> ccda-node x/text)]
          (if valattr (str "valattr was" valattr) text)))))

(defmethod to-fhir-dt "uri" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))


(defmethod to-fhir-dt "code" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "identifier" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "dateTime" [dt ccda-node]
  (( parse-time-point ccda-node) nil))

; 
;  A mapping context consists of... 
;  1. C-CDA context object (represents context-conduction behavior) -- consider this as atom for *binding*
;  2. C-CDA node "in focus" (which also links back to the whole doc)
;  3. Set of FHIR feed entries that have been created so far (?)
;  4. FHIR resource currently in the process of being created (?)
;
;  The primitive operation takes a mapping context and returns a set of "Candidate results".
;  Each candidate result contains:
;
;  - metadata-tagged FHIR structure -- meta with
;     :as [:Type | :Resource | :Primitive]
;     :path "Observation.appliesDateTime" <-- note this resolves [x] notation to fully specify value
;
;  - set of newly-created dependent resources for inclusion in feed upstream
;
;  The process by which candidate results are created is:
;
;  1. Evaluate all the types that are allowed for :fhir-mapping :path
;
;  2. For each type allowed, either
;     a. parsePrimitive on the current path, or
;     b. recursively map-context on the current node
;        using the appropriate C-CDA "template" maper.
;
;
;
;

;

;  (fill-in-temlate)  is a recursive function that ping-pongs with (map-context).
;
;  To build up results, iterate over a template's props, and (map-context) each of them.
;  For each result, determine which candidate mappings(s) to keep for mering.
;
;  * there is an opportunity here to apply "fix-ups" between (map-context) and merging in.
;  With luck these can be declarative lists of fix-ups specified in the mapping EDN
;
;  Then initialize a default strucutre and merge in each of the prop-level results.
;  Finally, for each selected candidate, merge all dependencies into the feed.


(defn fhir-path-to-kw [p]
  (keyword (str "fhir-type-" p)))

(def primitives #{"string" "code" "dateTime" "uri"})
(defn default-template-for [dt]
  (when-not (primitives dt) (keyword (str "fhir-type-" dt))))

(defn follow-path [ccda-nodes path]

     (swap! calls update-in [:follow] inc)
    (mapcat  #(apply x/xml-> % path) ccda-nodes))

(declare map-context)
(def counter (atom 1))

(defn fill-in-template [ccda-context ccda-node fhir-in-progress mapping-context]

  ;(println "filling in for"  mapping-context)
  (reduce (fn [acc prop]
            (let [at-path (follow-path [ccda-node] (prop :path))
                  filled (map-context ccda-context at-path acc prop )]
              
              (if (> (count filled) 0 )
                (assoc acc (str  (get-in prop [:fhir-mapping :path]) (swap! counter inc)) filled)
                acc))
                                        ; TODO: slot things in
                                        ; at the right places, rather
                                        ; than just slamming in.
            )  {} (mapping-context :props)))

(defn map-context
  "Returns a vector of FHIR data (primitives, compounds, or resources) each
   meta-tagged to indicate their FHIR type (e.g. {:path \"CodeableCon/orkingcept\"})"
  [ccda-context ccda-nodes fhir-in-progress mapping-context]
  ;(println "mapping context for " (count ccda-nodes) mapping-context)
  (let [fhir-path (get-in mapping-context [:fhir-mapping :path])
        fhir-dts (fhir/datatypes-for-path fhir-path)
        template (mapping-context :template)]

    (doall (for [dt  (fhir-dts :types)
                 node ccda-nodes
                 :let [template (or template (default-template-for dt))] ]
             (do
               (with-meta (if template
                            (fill-in-template ccda-context node fhir-in-progress (template/definitions template))
                            {:primitive (to-fhir-dt dt node)}) { :fhir-dt dt} ) 
               )))))


(defn pval [] (let  [f1 (first examples/parsed-files)
                     ts (->> f1 -all-nodes (mapcat #(x/xml-> % :code )))]
                (println "Got some ts " (count ts))
                (map-context nil ts {} {:fhir-mapping {:path "Observation.interpretation"}})
                ))

(time (def x (pval)))



(let [codes (follow-path  (->> (first examples/parsed-files) -all-nodes) [:code]  )]
  (println "Across " (count codes) "codes")
  (time (def xx
          (doall (for  [n (range 1000)]
                   (follow-path codes  [(x/attr :displayName)]))))))

(first xx)
