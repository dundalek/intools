(ns reconsierge.main
  (:gen-class))

(import '(org.graalvm.polyglot Context Source))

(def context-builder
  (doto (Context/newBuilder (into-array String ["js"]))
     (.option "js.timer-resolution" "1")
     (.option "js.java-package-globals" "false")
     (.out System/out)
     (.err System/err)
     (.allowAllAccess true)
     (.allowNativeAccess true)))

(def context (.build context-builder))

(defn execute-fn [context fn & args]
  (let [fn-ref (.eval context "js" fn)
        args (into-array Object args)]
    (assert (.canExecute fn-ref) (str "cannot execute " fn))
    (.execute fn-ref args)))

(def app-js (clojure.java.io/file "out/main.js"))
(def app-source (.build (Source/newBuilder "js" app-js)))
(.eval context app-source)

(defn -main [& _args]
  (def result (execute-fn context "my.app.html" "Polyglot Graal 🌈"))
  (println (.asString result))

  #_(println "XX Hello world!"))
