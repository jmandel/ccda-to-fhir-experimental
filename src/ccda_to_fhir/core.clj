(ns ccda-to-fhir.core
  (:use
    [clj-xpath.core :only [$x $x:tag $x:text $x:attrs $x:attrs* $x:node xml->doc]]
   )
  ( :require
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]

    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))

(defn by-template [d t]
  (let [q (str"//templateId[@root='" t "']/.."  )]
    ($x q  d)))

(defn parse-iso-time [t]
  (when t
    (if-let [pieces (re-find #"^(....)(..)?(..)?" t)]
      (apply str (interpose "-" (remove nil? (rest pieces)))))))

(defn map-to-fhir [{:keys [fhir-doc ccda-node mapping-context]}])

; for each prop in the template, call property-to-fhir
; then figure out how to slot that stuff into a return object

(defmulti to-fhir-dt (fn [dt ccda-node] dt))

(defmethod to-fhir-dt "string" [dt ccda-node]
  (if (= java.lang.String  (type ccda-node)) ccda-node 
      (let [valattr ($x:text "./@value" ccda-node)
            valtext ($x:text "./")]
        (first (remove empty? [valattr valtext])))))

(defmethod to-fhir-dt "uri" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "code" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "identifier" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "dateTime" [dt ccda-node]
  (parse-iso-time (to-fhir-dt "string" ccda-node)))

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


(def primitives #{"string" "code" "dateTime" "uri"})
(defn default-template-for [dt]
  (when-not (primitives dt) (keyword (str "fhir-type-" dt))))

(defn follow-path [ccda-node path]
  (apply $x path ccda-node))

(declare map-context)
(def counter (atom 1))

(defn fill-in-template [ccda-context ccda-node fhir-in-progress mapping-context]
  (reduce (fn [acc prop]
            (let [at-path (follow-path ccda-node (prop :path))
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
                     ts ($x "//code" f1 )]
                (map-context nil ts {} {:fhir-mapping {:path "Observation.interpretation"}})
                ))

(to-fhir-dt "string" "test")

(fhir/datatypes-for-path "CodeableConcept.text")
(type (first examples/parsed-files))

(def ts (->> (first examples/parsed-files)
         ($x "//code")))
(println (count ts))
(follow-path (take 1 ts) "./@displayName")
