(ns reitit.ring.middleware.muuntaja-test
  (:require [clojure.test :refer [deftest testing is]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.swagger :as swagger]
            [muuntaja.core :as m]))

(deftest muuntaja-test
  (let [data {:kikka "kukka"}
        app (ring/ring-handler
              (ring/router
                ["/ping" {:get (constantly {:status 200, :body data})}]
                {:data {:muuntaja m/instance
                        :middleware [muuntaja/format-middleware]}}))]
    (is (= data (->> {:request-method :get, :uri "/ping"}
                     (app)
                     :body
                     (m/decode m/instance "application/json"))))))

(deftest muuntaja-swagger-test
  (let [with-defaults m/instance
        no-edn-decode (m/create (-> m/default-options (update-in [:formats "application/edn"] dissoc :decoder)))
        just-edn (m/create (-> m/default-options (m/select-formats ["application/edn"])))
        app (ring/ring-handler
              (ring/router
                [["/defaults"
                  {:get identity}]
                 ["/explicit-defaults"
                  {:muuntaja with-defaults
                   :get identity}]
                 ["/no-edn-decode"
                  {:muuntaja no-edn-decode
                   :get identity}]
                 ["/just-edn"
                  {:muuntaja just-edn
                   :get identity}]
                 ["/form-params"
                  {:post {:parameters {:form {:x string?}}
                          :handler identity}}]
                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]
                {:data {:muuntaja m/instance
                        :middleware [muuntaja/format-middleware]}}))
        spec (fn [method path]
               (let [path (keyword path)]
                 (-> {:request-method :get :uri "/swagger.json"}
                     (app) :body
                     (->> (m/decode m/instance "application/json"))
                     :paths path method)))
        produces (comp set :produces (partial spec :get))
        consumes (comp set :consumes (partial spec :get))
        post-produces (comp set :produces (partial spec :post))
        post-consumes (comp set :consumes (partial spec :post))]

    (testing "with defaults"
      (let [path "/defaults"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)
               (consumes path)))))

    (testing "with explicit muuntaja defaults"
      (let [path "/explicit-defaults"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)
               (consumes path)))))

    (testing "without edn decode"
      (let [path "/no-edn-decode"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)))
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"}
               (consumes path)))))

    (testing "just edn"
      (let [path "/just-edn"]
        (is (= #{"application/edn"}
               (produces path)
               (consumes path)))))

    (testing "form parameters swagger-data"
      (let [path "/form-params"]
        (is (= #{}
               (post-produces path)
               (post-consumes path)))))))

(deftest muuntaja-swagger-parts-test
  (let [app (ring/ring-handler
              (ring/router
                [["/request"
                  {:middleware [muuntaja/format-negotiate-middleware
                                muuntaja/format-request-middleware]
                   :get identity}]
                 ["/response"
                  {:middleware [muuntaja/format-negotiate-middleware
                                muuntaja/format-response-middleware]
                   :get identity}]
                 ["/both"
                  {:middleware [muuntaja/format-negotiate-middleware
                                muuntaja/format-response-middleware
                                muuntaja/format-request-middleware]
                   :get identity}]
                 ["/form-request"
                  {:middleware [muuntaja/format-negotiate-middleware
                                muuntaja/format-request-middleware]
                   :post {:parameters {:form {:x string?}}
                          :handler identity}}]
                 ["/form-response"
                  {:middleware [muuntaja/format-negotiate-middleware
                                muuntaja/format-response-middleware]
                   :post {:parameters {:form {:x string?}}
                          :handler identity}}]
                 ["/form-with-both"
                  {:middleware [muuntaja/format-negotiate-middleware
                                muuntaja/format-response-middleware
                                muuntaja/format-request-middleware]
                   :post {:parameters {:form {:x string?}}
                          :handler identity}}]

                 ["/swagger.json"
                  {:get {:no-doc true
                         :handler (swagger/create-swagger-handler)}}]]
                {:data {:muuntaja m/instance}}))
        spec (fn [method path]
               (-> {:request-method :get :uri "/swagger.json"}
                   (app) :body :paths (get path) method))
        produces (comp :produces (partial spec :get))
        consumes (comp :consumes (partial spec :get))
        post-produces (comp :produces (partial spec :post))
        post-consumes (comp :consumes (partial spec :post))]

    (testing "just request formatting"
      (let [path "/request"]
        (is (nil? (produces path)))
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (consumes path)))))

    (testing "just response formatting"
      (let [path "/response"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)))
        (is (nil? (consumes path)))))

    (testing "request and response formatting"
      (let [path "/both"]
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (produces path)))
        (is (= #{"application/json"
                 "application/transit+msgpack"
                 "application/transit+json"
                 "application/edn"}
               (consumes path)))))
    (testing "just request formatting for form params"
      (let [path "/form-request"]
        (is (nil? (post-produces path)))
        (is (nil? (post-consumes path)))))
    (testing "just response formatting for form params"
      (let [path "/form-response"]
        (is (nil? (post-produces path)))
        (is (nil? (post-consumes path)))))
    (testing "just request formatting for form params"
      (let [path "/form-with-both"]
        (is (nil? (post-produces path)))
        (is (nil? (post-consumes path)))))))
