(ns ccda-to-fhir.core
  ( :use [ccda-to-fhir.query :only [follow-property follow-xpath]])
  ( :require
    [clojure.pprint :as pprint]
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] ))

(defn parse-iso-time [t]
                                        ; (print "parsing isoetme " t)
  (when t
    (if-let [pieces (re-find #"^(....)(..)?(..)?" t)]
      (apply str (interpose "-" (remove nil? (rest pieces)))))))

(defmulti to-fhir-dt (fn [dt xml-node] dt))

(defmethod to-fhir-dt "string" [dt xml-node]
                                        ;(println "Converting -> string " (type xml-node))
  (cond
   (nil? xml-node) nil
   (= java.lang.String  (type xml-node)) xml-node 
   :else (let [valattr (x/xml1-> xml-node (x/attr :value))
               text (x/xml1-> xml-node x/text)]
           (or valattr text))))

(defmethod to-fhir-dt "uri" [dt xml-node]
  (to-fhir-dt "string" xml-node))

(defmethod to-fhir-dt "code" [dt xml-node]
  (to-fhir-dt "string" xml-node))

(defmethod to-fhir-dt "identifier" [dt xml-node]
  (to-fhir-dt "string" xml-node))

(defmethod to-fhir-dt "dateTime" [dt xml-node]
  (parse-iso-time (to-fhir-dt "string" xml-node)))

(defmethod to-fhir-dt "time" [dt xml-node]
  (to-fhir-dt "string" xml-node))

(def primitives #{"string" "code" "dateTime" "uri" "time"})

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
                                        ;(println "next-context for " base)
    (-> desc
        (merge base)
        (dissoc :content-from)))) 

(declare apply-context)

(defn fill-field-extensions [extensions] "TODO")

(defmulti fill-one-field
  (fn [bundle xml-node context-stack [name desc]] 
    (if (instance? java.lang.String name) :simple-field name)))

(defmethod fill-one-field :one-of
  [bundle xml-node context-stack field]
  (let [children (follow-xpath [xml-node])]
    "TODO"))

(defn fill-one-field-desc 
  [bundle xml-node context-stack [name desc]]
                                        ;  (println "fill one field"  name desc)
  (if-let [fixed-val (get desc :content-fixed)]
    [fixed-val]
    (let [
          fhir-path (fhir-path-for context-stack name)
          dt (-> fhir-path fhir/path-to-datatypes first)
          primitive? (primitives dt)
          extensions (map get-context (get desc :extension))
          next-context (next-context-for dt desc)
                                        ;  _ (println "filling " next-context fhir-path dt primitive? extensions)
          ]
      (doall
       (for [node (follow-xpath [xml-node] next-context)]
         (if primitive? {:value (to-fhir-dt dt node)
                         :extension (fill-field-extensions extensions)}
             (do 
                                        ;(println "next-context to apply" next-context)
               (apply-context bundle node (cons next-context context-stack)))))))))

(defmethod fill-one-field :simple-field
  [bundle xml-node context-stack [name desc]]
  (let [descs (if (vector? desc) desc [desc])]
    (apply concat
           (for [desc descs]
             (fill-one-field-desc
              bundle
              xml-node
              context-stack
              [name desc])))))

(def counter (atom 0))

(defn merge-one-field [ bundle context-stack acc [field-name val]]
  (assoc acc (str  field-name ) val))

(defn apply-context-to [kw bundle xml-node context-stack]
  (let [fill (partial fill-one-field bundle xml-node context-stack)
        fields (get (first context-stack) kw)
        filled (map fill fields)
        field-names (map first fields)
        filled-map (map vector field-names filled)]
    filled-map))


(defn resource-level-context? [context-stack]
  (-> context-stack first :fhir fhir/path-to-datatypes first (= "Resource")))

(def counter (atom 0))
(defn nextid []
  (let [num (swap! counter inc)] (str "cid:" num)))

(defn add-resource-to-bundle [resource bundle]
  (println "adding restob" (type resource) (type bundle))
  (let [id (nextid)]
    (swap! bundle assoc id resource)
    {:value {"reference" id} :extension nil}))

(defn apply-context [bundle xml-node context-stack]
                                        ;(println "Apply context " (first context-stack))
  (let [value-fields (apply-context-to :content bundle xml-node context-stack)
        extension-fields (apply-context-to :extension bundle xml-node context-stack)
        resource? (resource-level-context? context-stack)
        merge (partial merge-one-field bundle context-stack)
        merged-val {
                    :value (reduce merge {} value-fields)
                    :extension (reduce merge {} extension-fields)
                    }]
    (if resource? (add-resource-to-bundle merged-val bundle) merged-val)))

(defn parse-ccda [root-element]
  (let [bundle (atom {})
        default-context (list (template/contexts :ccda))]
    (apply-context bundle root-element default-context)
    @bundle))
