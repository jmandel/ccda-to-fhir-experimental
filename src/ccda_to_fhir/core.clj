(ns ccda-to-fhir.core
  (:use
    [clj-xpath.core :only [$x $x:tag $x:text $x:text? $x:attrs $x:attrs* $x:node xml->doc xp:compile]]
   )
  ( :require
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]


    [clojure.zip :as zip]
    [clojure.data.zip :as dz])
  (
   :import            [org.w3c.dom                 Document Node]
                    [javax.xml.xpath             XPathFactory XPathConstants XPathExpression]
                      )
 
  )

(def calls (atom {:template 0 :follow 0 :text 0}))

(defn by-template [d t]
  (swap! calls update-in [ :template] inc)
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
  (if (= java.lang.String  (type ccda-node))
    ccda-node 
    (do (:text ccda-node))))


(defmethod to-fhir-dt "uri" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "code" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "identifier" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "datetime" [dt ccda-node]
  (parse-iso-time (to-fhir-dt "string" ccda-node)))

; 
;  a mapping context consists of... 
;  1. c-cda context object (represents context-conduction behavior) -- consider this as atom for *binding*
;  2. c-cda node "in focus" (which also links back to the whole doc)
;  3. set of fhir feed entries that have been created so far (?)
;  4. fhir resource currently in the process of being created (?)
;
;  the primitive operation takes a mapping context and returns a set of "candidate results".
;  each candidate result contains:
;
;  - metadata-tagged fhir structure -- meta with
;     :as [:type | :resource | :primitive]
;     :path "observation.appliesdatetime" <-- note this resolves [x] notation to fully specify value
;
;  - set of newly-created dependent resources for inclusion in feed upstream
;
;  the process by which candidate results are created is:
;
;  1. evaluate all the types that are allowed for :fhir-mapping :path
;
;  2. for each type allowed, either
;     a. parseprimitive on the current path, or
;     b. recursively map-context on the current node
;        using the appropriate c-cda "template" maper.
;
;
;
;

;

;  (fill-in-temlate)  is a recursive function that ping-pongs with (map-context).
;
;  to build up results, iterate over a template's props, and (map-context) each of them.
;  for each result, determine which candidate mappings(s) to keep for mering.
;
;  * there is an opportunity here to apply "fix-ups" between (map-context) and merging in.
;  with luck these can be declarative lists of fix-ups specified in the mapping edn
;
;  then initialize a default strucutre and merge in each of the prop-level results.
;  finally, for each selected candidate, merge all dependencies into the feed.


(def primitives #{"string" "code" "datetime" "uri"})
(defn default-template-for [dt]
  (when-not (primitives dt) (keyword (str "fhir-type-" dt))))

(defn follow-path [ccda-node path]
  ;(println "following an xml thing of type " (instance? Document ( ccda-node :node)))
  (let [ cp (xp:compile path)
        node (:node ccda-node)]
   ; (time (.evaluate ^javax.xml.xpath.XPathExpression cp node
   ; XPathConstants/NODESET))
    )

  (swap! calls update-in [:follow] inc)
  ($x path ccda-node))

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

(time (def x (doall (pval))))

;(to-fhir-dt "string" "test")

;(fhir/datatypes-for-path "CodeableConcept.text")
;(type (first examples/parsed-files));

;
;(def ts (->>
 ;        (first examples/parsed-files)
 ;        ($x "//code")
;))

;(println (count ts))
;(type (first examples/parsed-files))
;($x "//code" (first examples/parsed-files))
;(type (take 1 ts))
;(println (follow-path (first ts) "./@displayName"))

;
; (let [dn (xp:compile "//@displayName") ]
; (time (def dd (doall (for [n (range 1000)]
;                       (doall ($x dn (first examples/parsed-files))))))))
