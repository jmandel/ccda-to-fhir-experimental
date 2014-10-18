(ns ccda-to-fhir.core
  ( :use [ccda-to-fhir.query :only [follow-property follow-xpath]])
  ( :require
    [clojure.pprint :as pprint]
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] 
    [clojure.zip :as zip]))

(defn parse-iso-time [t]
  (print "parsing isoetme " t)
  (when t
    (if-let [pieces (re-find #"^(....)(..)?(..)?" t)]
      (apply str (interpose "-" (remove nil? (rest pieces)))))))

(defmulti to-fhir-dt (fn [dt ccda-node] dt))

(defmethod to-fhir-dt "string" [dt ccda-node]
  (println "Converting -> string " (type ccda-node))
  (cond
   (nil? ccda-node) nil
   (= java.lang.String  (type ccda-node)) ccda-node 
   :else (let [valattr (x/xml1-> ccda-node (x/attr :value))
               text (x/xml1-> ccda-node x/text)]
           (or valattr text))))

(defmethod to-fhir-dt "uri" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "code" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "identifier" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "dateTime" [dt ccda-node]
  (parse-iso-time (to-fhir-dt "string" ccda-node)))

(defmethod to-fhir-dt "time" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(def primitives #{"string" "code" "dateTime" "uri" "time"})

(defn default-template-for [dt]
  (let [tpath (keyword (str "fhir-type-" dt))
        template (template/definitions tpath)]
    (if template template nil)))

(declare map-context)
(defn pick-best-candidate [prop base candidate-vals]
  (let  [candidates
         (doall (->> candidate-vals
                     (remove #(or (empty? %) (empty? (get  % :value))))))]
    (cond (< 1  (count candidates )) 
          (do
            (println "failing for candidates set")
            (throw (Exception. "Can't decide among >1 candidate")))
          (= 0 (count candidates))
          (println "No candiets for " prop base candidate-vals)
          candidates (first candidates))))

(defn merge-points-compatible [a b] true) ; TODO

(defn last-path-component [p]
  (first (reverse (clojure.string/split p #"\."))))

(defn merge-val-into-fhir [prop base candidate-vals]
  (println "merging val into " prop base candidate-vals)
  (let [merge-point (get-in prop [ :fhir-mapping :path])
        candidate-vals (doall candidate-vals)
        _ (println "pick best" merge-point " among " candidate-vals "...") 
        best-candidate (pick-best-candidate prop base candidate-vals)
        _ (println "bst was" best-candidate)
        proposed-merge-point (get best-candidate :path)]

    (if best-candidate
      (update-in base [(str (last-path-component proposed-merge-point))] (fnil conj [])  best-candidate)
      base)))

(defn merge-val-sets-into-fhir [prop base val-sets]
  (reduce (partial merge-val-into-fhir prop) base val-sets))

(defn fill-in-template [ccda-context ccda-node fhir-in-progress template]
  (println "filling in for"  template "with context " fhir-in-progress)
  (let [top-level-object (get-in template [:fhir-mapping :path])
        props (get-in template [:props])
        _ (println "got # props " (count props))
        all-props
        (reduce (fn [acc prop]
                  (let [at-path (follow-property [ccda-node] prop)
                        filled (map-context ccda-context at-path acc prop )]
                    (println "filled in " filled)
                    (if (> (count filled) 0 )
                      (merge-val-sets-into-fhir prop acc  filled)
                      acc))
                  )  {} props)]
    (when-not (empty? all-props )
      all-props)))

(defn ways-to-parse [mapping-context]
  (let [template (mapping-context :template)
        fhir-path (get-in mapping-context [:fhir-mapping :path])]
    (if template
      [{:path fhir-path :template (template/definitions template)}]

      (for [dt (fhir/path-to-datatypes fhir-path)
            :let [fhir-path (fhir/concrete-path-for-dt fhir-path dt)]]
        {:path fhir-path
         :primitive (primitives dt)
         :template (default-template-for dt)}))))

(defn map-context
  "For each starting node, returns a vector of FHIR data (primitives, compounds, or resources) each
   meta-tagged to indicate their FHIR type (e.g. {:path \"CodeableCon/orkingcept\"})"
  [ccda-context ccda-nodes fhir-in-progress mapping-context]
  (println "mapping context for " (count ccda-nodes) mapping-context)

  (let [ways (ways-to-parse mapping-context)]
    (for [node ccda-nodes] 
      (for [way ways :let [primitive (way :primitive) template (way :template)]]
        (if primitive
          {:path (way :path) :value (to-fhir-dt primitive node)}
          {:path (way :path) :value (fill-in-template ccda-context node mapping-context template)})))))

(defmulti fill-one-field
  (fn [ccda-resources ccda-node context-stack [name desc]] 
    (if (instance? java.lang.String name) :simple-field name)))

(defmethod fill-one-field :one-of
  [ccda-resources ccda-node context-stack field]
  (let [children (follow-xpath [ccda-node])]
    "TODO"))

(defn get-context [id]
  (get template/definitions id))

(defn fhir-path-for [context-stack name]
  (str (:fhir (first context-stack)) "." name))

(defn default-context-for [dt desc]
  (let [type-name (or (get desc :reference) dt)]
    (when type-name
      (keyword (str "fhir-type-" type-name)))))

(defn next-context-for [dt desc]
  (let [specified (-> desc (get :content-from) template/contexts)
        default (default-context-for dt desc)
        base (or specified (-> default template/contexts))]
    (println "next-context for " base)
    (-> desc
        (merge base)
        (dissoc :content-from)
        ))) 

(declare apply-context)

(defn fill-field-extensions [extensions]
  nil)

(defn fill-one-field-desc 
  [ccda-resources ccda-node context-stack [name desc]]
  (println "fill one field"  name desc)
  (if-let [fixed-val (get desc :content-fixed)]
    [fixed-val]
    (let [
          fhir-path (fhir-path-for context-stack name)
          dt (-> fhir-path fhir/path-to-datatypes first)
          primitive? (primitives dt)
          extensions (map get-context (get desc :extension))
          next-context (next-context-for dt desc)
          _ (println "filling " next-context fhir-path dt primitive? extensions)
          ]
      (doall
       (for [node (follow-xpath [ccda-node] next-context)]
         (if primitive? {:value (to-fhir-dt dt node)
                         :extension (fill-field-extensions extensions)}
             (do 
               (println "next-context to apply" next-context)
                (apply-context ccda-resources node (cons next-context context-stack))
               )))))))

(defmethod fill-one-field :simple-field
  [ccda-resources ccda-node context-stack [name desc]]
  (let [descs (if (vector? desc) desc [desc])]
    (apply concat
           (for [desc descs]
             (fill-one-field-desc
              ccda-resources
              ccda-node
              context-stack
              [name desc])))))

(def counter (atom 0))

(defn merge-one-field [ ccda-resources context-stack acc [field-name val]]
  (assoc acc (str  field-name ) val))

(defn apply-context-to [kw ccda-resources ccda-node context-stack]
  (let [fill (partial fill-one-field ccda-resources ccda-node context-stack)
        fields (get (first context-stack) kw)
        filled (map fill fields)
        field-names (map first fields)
        filled-map (map vector field-names filled)]
    filled-map))

(defn apply-context [ccda-resources ccda-node context-stack]
  (println "Apply context " (first context-stack))
  (let [value-fields (apply-context-to :content ccda-resources ccda-node context-stack)
        extension-fields (apply-context-to :extension ccda-resources ccda-node context-stack)
        merge (partial merge-one-field ccda-resources context-stack)]
    {
     :value (reduce merge {} value-fields)
     :extension (reduce merge {} extension-fields)
     })) 

(defn parse-ccda [root-element]
  (apply-context
   (atom {})
   root-element
   (list (template/contexts :ccda))))
