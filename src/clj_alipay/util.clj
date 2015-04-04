(ns clj-alipay.util
  (:use [hiccup.core])
  (:require [noir.util.crypt :refer [md5]]
            [hiccup.page :refer [html5]]
            [taoensso.timbre :as timbre]
            [clj-http.client :as client]))

(defn- servlet-context
  [req]
  (if-let [context (:servlet-context req)]
    (try (.getContextPath context)
      (catch IllegalArgumentException _ context))))

(defn- alipay-req
  [alipay ali-req req]
  (let [url (str (-> req :scheme name)
                 "://"
                 (get-in req [:headers "host"])
                 (servlet-context req))]
    (->
     (dissoc alipay :key :notify :return)
     (merge ali-req)
     (assoc
       :service "create_direct_pay_by_user"
       :payment_type "1"
       :_input_charset "utf-8"
       :seller_id (:partner alipay)
       :notify_url (str url (:notify alipay))
       :return_url (str url (:return alipay))))))

(defn- get-sign-string
  "生成待签名字符串"
  [req]
  (->>
   (dissoc req :sign :sign_type)
   (filter (fn [[k v]]
             (if (string? v)
               (not (empty? v))
               v))) ;去除参数为空
   (into (sorted-map))   ;排序
   (map (fn [ [ k v ]] (str (name k) "=" v)))
   (clojure.string/join "&")))

(defn create-sign [request key]
  (-> request
      (get-sign-string)
      (str key)
      (md5)))

(defn create-alipay-request
  [alipay ali-req request]
  (let [req (alipay-req alipay ali-req request)
        sign (create-sign req (:key alipay))]
    (assoc req :sign sign :sign_type "MD5")))

(defn post-alipay
  "转到支付宝支付，alipay 为支付宝参数
  req 至少有 out_trade_no 订单号 subject 商品名称 total_fee 金额"
  [alipay {:keys [out_trade_no subject total_fee] :as req} request]
  {:pre [(and out_trade_no subject total_fee)]}
  (let [req (create-alipay-request alipay req request)]
    (html5
     [:head
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=utf-8"}]
      [:title "支付宝即时到账交易接口接口"]]
     [:body
      [:form {:mothod "post"
              :action (str "https://mapi.alipay.com/gateway.do?" "_input_charset=utf-8")
              :id "alipaysubmit"}
       (for [[k v] req]
         [:input {:type "hidden" :name (name k) :value v}])
       [:input {:type "submit" :value "确认" :style "display:none;"}]
       [:script "document.forms['alipaysubmit'].submit();"]]])))

(defn alipay-return? [alipay req]
  (or
   (= (:uri req) (str (servlet-context req) (:notify alipay)))
   (= (:uri req) (str (servlet-context req) (:return  alipay)))))

(defn alipay-virity [alipay req]
  (=
   (->   (:params req)
         (create-sign (:key alipay)))
   (get-in req [:params :sign])))

(defn from-alipay?
  "验证是否是支付宝发来的消息"
  [alipay notify_id]
  (= (:body (client/get
             "https://mapi.alipay.com/gateway.do"
             {:query-params {:service "notify_verify"
                             :partner (:partner alipay)
                             :notify_id notify_id}}))
     "true"))
