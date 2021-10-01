(ns portal.runtime.jvm.launcher
  (:require [clojure.edn :as edn]
            [org.httpkit.client :as client]
            [org.httpkit.server :as http]
            [portal.runtime :as rt]
            [portal.runtime.browser :as browser]
            [portal.runtime.fs :as fs]
            [portal.runtime.jvm.client :as c]
            [portal.runtime.jvm.server :as server]))

(defn- vs-code-config []
  (some-> ".portal/vs-code.edn" fs/exists slurp edn/read-string))

(defmethod browser/-open :vs-code [{:keys [portal options server]}]
  (when-let [{:keys [host port]} (vs-code-config)]
    (client/post (str "http://" host ":" port "/open")
                 {:body (pr-str {:portal  portal
                                 :options options
                                 :server  (select-keys server [:host :port])})})))

(defonce ^:private server (atom nil))

(defn start [options]
  (or @server
      (let [{:portal.launcher/keys [port host]
             :or {port 0 host "localhost"}} options
            http-server (http/run-server #'server/handler
                                         {:port port
                                          :max-ws (* 1024 1024 1024)
                                          :legacy-return-value? false})]
        (reset!
         server
         {:http-server http-server
          :port (http/server-port http-server)
          :host host}))))

(defn open
  ([options]
   (open nil options))
  ([portal options]
   (let [server (start options)]
     (browser/open {:portal portal :options options :server server}))))

(defn clear []
  (c/request {:op :portal.rpc/clear})
  (swap! rt/sessions select-keys (keys @c/connections)))

(defn close []
  (c/request {:op :portal.rpc/close})
  (future
    (some-> server deref :http-server http/server-stop!))
  (reset! server nil)
  (reset! rt/sessions {}))

(reset! rt/request c/request)
