(ns yada.authentication
  (:require
   [clojure.string :as str]
   [manifold.deferred :as d]
   [yada.syntax :as syn]
   [yada.context :as ctx]))

(defn call-fn-maybe [x ctx]
  (when x
    (if (fn? x) (x ctx) x)))

(defn resolve-authenticators-at-request
  "At request time, resolve any dynamic authenticators (that need to be
  constructed per-request)."
  [ctx authenticators]
  (let [authenticators
        (if (fn? authenticators)
          (let [res (authenticators ctx)]
            ;; ensure dynamic function has returned a
            ;; sequential.
            (assert (sequential? res))
            res)
          authenticators)]
    ;; doall, because we want errors to bubble out now
    (doall (keep #(call-fn-maybe % ctx) authenticators))))

(defn authenticate
  [ctx authenticators]
  (d/chain
   (->> authenticators
        (keep (fn [{::keys [scheme authenticate] :as authenticator}]
                (when (and authenticate (or (not (string? scheme)) (.equalsIgnoreCase scheme (::syn/auth-scheme (ctx/authorization ctx)))))
                  (authenticate ctx))))
        (apply d/zip))
   (fn [results]
     (->> results
          (map (fn [authenticator result] (when result (merge authenticator result))) authenticators)
          (remove nil?))))

  ;; An iterative approach, decided to instead go for a parallel
  ;; version.
  #_(d/loop [authenticators authenticators]
      (when (seq authenticators)
        (let [{::keys [scheme authenticate]} (first authenticators)]
          (d/chain
           (when (and
                  authenticate
                  ;; Only call those that have matching HTTP scheme
                  (or (not (string? scheme)) (.equalsIgnoreCase scheme (::syn/auth-scheme (ctx/authorization ctx)))))
             (authenticate ctx))
           #(if (some? %)
              ;; TODO: Process return value (in caller, via a helper
              ;; function - keep this function simple to facilitate
              ;; testing)
              %
              (d/recur (next authenticators))))))))

(defn challenge-str
  [ctx authenticators]
  (syn/format-challenges
   (keep
    (fn [{::keys [scheme challenge]}]
      (when challenge
        (merge {:scheme scheme} (challenge ctx))))
    (sort-by #(get % ::challenge-order Integer/MAX_VALUE) authenticators))))
