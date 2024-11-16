(ns cognitect.test-runner
  (:require [clojure.set :as set]
            [clojure.tools.namespace.find :as find]
            [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.cli :as cli])
  (:refer-clojure :exclude [test]))

(defn- ns-filter
  [{:keys [namespace namespace-regex]}]
  (let [[include-ns include-regexes]
        (if (or (seq namespace) (seq namespace-regex))
          [namespace namespace-regex]
          [nil [#".*\-test$"]])]
    (fn [ns]
      (or
        (get include-ns ns)
        (some #(re-matches % (name ns)) include-regexes)))))

(defn- var-filter
  [{:keys [var include exclude]}]
  (let [test-specific (if var
                        (set (map #(or (resolve %)
                                       (throw (ex-info (str "Could not resolve var: " %)
                                                       {:symbol %})))
                                  var))
                        (constantly true))
        test-inclusion (if include
                         #((apply some-fn include) (meta %))
                        (constantly true))
        test-exclusion (if exclude
                         #((complement (apply some-fn exclude)) (meta %))
                         (constantly true))]
    #(and (test-specific %)
          (test-inclusion %)
          (test-exclusion %))))

(defn- filter-vars!
  [nses filter-fn]
  (doseq [ns nses]
    (doseq [[_name var] (ns-publics ns)]
      (when (:test (meta var))
        (when (not (filter-fn var))
          (alter-meta! var #(-> %
                                (assoc ::test (:test %))
                                (dissoc :test))))))))

(defn- restore-vars!
  [nses]
  (doseq [ns nses]
    (doseq [[_name var] (ns-publics ns)]
      (when (::test (meta var))
        (alter-meta! var #(-> %
                              (assoc :test (::test %))
                              (dissoc ::test)))))))

(defn- contains-tests?
  "Check if a namespace contains some tests to be executed."
  [pred ns]
  (some pred (-> ns ns-publics vals)))

(defn test
  [options]
  (let [dirs (or (:dir options)
                 #{"test"})
        nses (->> dirs
                  (map io/file)
                  (mapcat find/find-namespaces-in-dir))
        nses (filter (ns-filter options) nses)
        lazy-find (try (requiring-resolve 'lazytest.find/find-var-test-value)
                       (catch Exception _ nil))
        lazy-run
        (try (requiring-resolve 'lazytest.repl/run-tests)
             (catch Exception _ nil))
        lazy-reporters
        (try @(requiring-resolve 'lazytest.reporters/nested)
             (catch Exception _ nil))
        lazy-opts {:var :var-filter :namespace :ns-filter :output :reporter}]
    (println (format "\nRunning tests in %s" dirs))
    (dorun (map require nses))
    (try
      (filter-vars! nses (var-filter options))
      (cond->> {:fail 0 :error 0}
        :clojure.test
        (merge-with + (when-let [nses-with-tests (seq (filter #(contains-tests? (comp :test meta) %) nses))]
                        (apply test/run-tests nses-with-tests)))
        (and lazy-find lazy-run)
        (merge-with + (when-let [nses-with-tests (seq (filter #(contains-tests? lazy-find %) nses))]
                        (lazy-run nses-with-tests
                                  (merge {:reporter [lazy-reporters]}
                                         (set/rename-keys options lazy-opts))))))
      (finally
        (restore-vars! nses)))))

(defn- parse-kw
  [^String s]
  (if (.startsWith s ":") (read-string s) (keyword s)))


(defn- accumulate [m k v]
  (update-in m [k] (fnil conj #{}) v))

(def cli-options
  [["-d" "--dir DIRNAME" "Name of the directory containing tests. Defaults to \"test\"."
    :parse-fn str
    :assoc-fn accumulate]
   ["-n" "--namespace SYMBOL" "Symbol indicating a specific namespace to test."
    :parse-fn symbol
    :assoc-fn accumulate]
   ["-r" "--namespace-regex REGEX" "Regex for namespaces to test (clojure.test-only)."
    :parse-fn re-pattern
    :assoc-fn accumulate]
   ["-v" "--var SYMBOL" "Symbol indicating the fully qualified name of a specific test."
    :parse-fn symbol
    :assoc-fn accumulate]
   ["-i" "--include KEYWORD" "Run only tests that have this metadata keyword."
    :parse-fn parse-kw
    :assoc-fn accumulate]
   ["-e" "--exclude KEYWORD" "Exclude tests with this metadata keyword."
    :parse-fn parse-kw
    :assoc-fn accumulate]
   [nil "--output SYMBOL" "Output format (LazyTest-only). Can be given multiple times. (Defaults to \"nested\".)"
    :parse-fn read-string
    :assoc-fn (fn [args k v]
                (let [output (if (qualified-symbol? v)
                               v
                               (symbol "lazytest.reporters" (name v)))]
                  (update args k (comp vec distinct (fnil conj [])) output)))]
   ["-H" "--test-help" "Display this help message"]])

(defn- help
  [args]
  (println "\nUSAGE:\n")
  (println "clj -m" (namespace `help) "<options>\n")
  (println (:summary args))
  (println "\nAll options may be repeated multiple times for a logical OR effect.")
  (println "If neither -n nor -r is supplied, use -r #\".*-test$\" (ns'es ending in '-test')"))

(defn -main
  "Entry point for the test runner"
  [& args]
  (let [args (cli/parse-opts args cli-options)]
    (if (:errors args)
      (do (doseq [e (:errors args)]
            (println e))
          (help args)
          (System/exit 1))
      (if (-> args :options :test-help)
        (help args)
        (try
          (let [{:keys [fail error]} (test (:options args))]
            (System/exit (if (zero? (+ fail error)) 0 1)))
          (finally
            ;; Only called if `test` raises an exception
            (shutdown-agents)))))))
