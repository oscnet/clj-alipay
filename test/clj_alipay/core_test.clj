(ns clj-alipay.core-test
 (:import [java.net URLEncoder URLDecoder])
  (:use ring.mock.request)
  (:require [clojure.test :refer :all]
            [clj-alipay.core :refer :all]
            [clj-alipay.util :refer :all]
            [compojure.core :refer :all]
            [noir.util.middleware :refer [app-handler]]
            [clj-http.fake :as fake]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults]]))

(def alipay
  {:partner "2088...4112324183"
   :key "rqvere...ab6yxtbczy9jm"
   :notify "/alipay/notify"
   :return "/alipay/return"})

(defn return [req]
  (println req))

(defn notify [req]
  "success")

(defn pay-page []
  (pay alipay {:out_trade_no "001010"
               :subject "商品名称"
               :total_fee "0.10"}))

(defroutes home-routes
  (GET "/pay" [] (pay-page))
  (POST "/alipay/notify" [] notify)
  (GET "/alipay/return" [] return)
  (GET "/" [] "ok"))

(def app (app-handler
          [home-routes]
          :middleware [wrap-anti-forgery #(wrap-alipay % alipay)]
          :ring-defaults
          (assoc-in site-defaults [:security :anti-forgery] false)
          :access-rules []
          :formats [:json-kw :edn :transit-json]))

(def notify-req
  {:seller_id "2088...4183",
   :seller_email "foo@163.com",
   :sign "f5f2572c2c7bb9918d75cc8d8ff5e345",
   :trade_no "201504....000450049507600",
   :use_coupon "N",
   :notify_type "trade_status_sync",
   :buyer_id "208....288454",
   :sign_type "MD5",
   :payment_type "1",
   :buyer_email "foo@foo.com",
   :total_fee "0.06",
   :out_trade_no "10",
   :notify_id "ead7...64feb4i",
   :gmt_payment "2015-04-04 08:25:05",
   :is_total_fee_adjust "N",
   :trade_status "TRADE_SUCCESS",
   :gmt_create "2015-04-04 08:07:27",
   :quantity "1",
   :discount "0.00",
   :notify_time "2015-04-04 08:29:13",
   :price "0.06",
   :subject "macbook"})

(defn create-request-map
  "根据接口生成支付宝返回参数"
  []
  (->
   "is_success=T&sign=b1af584504b8e845ebe40b8e0e733729&sign_type=MD5&body=Hello&buyer_email=xinjie_xj%40163.com&buyer_id=2088101000082594&exterface=create_direct_pay_by_user&out_trade_no=6402757654153618&payment_type=1&seller_email=chao.chenc1%40alipay.com&seller_id=2088002007018916&subject=%E5%A4%96%E9%83%A8FP&total_fee=10.00&trade_no=2014040311001004370000361525&trade_status=TRADE_FINISHED&notify_id=RqPnCoPT3K9%252Fvwbh3I%252BODmZS9o4qChHwPWbaS7UMBJpUnBJlzg42y9A8gQlzU6m3fOhG&notify_time=2008-10-23+13%3A17%3A39&notify_type=trade_status_sync&extra_common_param=%E4%BD%A0%E5%A5%BD%EF%BC%8C%E8%BF%99%E6%98%AF%E6%B5%8B%E8%AF%95%E5%95%86%E6%88%B7%E7%9A%84%E5%B9%BF%E5%91%8A%E3%80%82"
   (URLDecoder/decode)
   (clojure.string/split #"&")
   (->>
    (map #(clojure.string/split % #"="))
    (into (sorted-map))
    (clojure.walk/keywordize-keys))))

(deftest virity-test
  (testing "函数测试 alipay-virity"
    (let [req (create-request-map)
          sign (create-sign req (:key alipay))
          req (assoc req :sign sign)]
      (is (= true (alipay-virity alipay {:params req}))))))

(deftest test-app
  (testing "create-alipay-request"
    (is (= "c5cb6a923153f112e5d956431638a81a" (:sign (create-alipay-request alipay {:out_trade_no 1 :subject "商品名称" :total_fee 1.16} {:scheme "http" :headers {"host" "localhost"}})))))
  (testing "first page"
    (let [response (app (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "请求支付宝支付"
    (let [response (app (request :get "/pay"))]
      (is (= 200 (:status response)))))

  (testing "from-alipay?"
    (fake/with-fake-routes {#"https://mapi.alipay.com/gateway.do.*" (fn [request] {:status 200 :headers {} :body "true"})}
      (is (= true (from-alipay? alipay "3444")))
      (is (= true (from-alipay? alipay "!@##887!@#")))))

  (testing "notify"
    (fake/with-fake-routes {#"https://mapi.alipay.com/gateway.do.*" (fn [request] {:status 200 :headers {} :body "true"})}
      (let [response (app (request :post "/alipay/notify" notify-req))]
      (is (= "success" (:body response)))))))
