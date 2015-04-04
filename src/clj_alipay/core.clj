(ns clj-alipay.core
  (:use [hiccup.core]
        [clj-alipay.util])
  (:require [ring.util.response :refer [content-type response not-found]]
            [taoensso.timbre :as timbre]
            [crypto.random :as random]
            [compojure.response :refer [Renderable]]))

(deftype RenderAlipay [alipay alipay-request]
  Renderable
  (render [this request]
          (content-type
           (response (post-alipay alipay alipay-request request))
           "text/html; charset=utf-8")))

(defn pay
  "转到支付宝付款 alipay 为{:keys [:partner :key :notify :return]}
  notify 支付宝发送的 notify path
  return 支付后返回的页面
  req 至少有 out_trade_no 订单号 subject 商品名称 total_fee 金额 等项"
  [alipay req]
  (RenderAlipay. alipay req))

(defn- get-request? [{method :request-method}]
  (or (= method :head)
      (= method :get)
      (= method :options)))

(defn- new-token []
  (random/base64 60))

(defn fake-anti-forgery
  "当使用anti-forgery时,会引起支付宝的 notify POST不成功."
  [request]
  (if (get-request? request)
    request
    (let [token (new-token)]
      (-> request
          (assoc-in [:session :ring.middleware.anti-forgery/anti-forgery-token] token)
          (assoc-in [:headers "x-csrf-token"] token)))))

(defn wrap-alipay
  "对支付宝发过来的信息自动进行校验处理"
  [hander alipay]
  (fn [request]
    (if (alipay-return? alipay request)
      (if (and
           (or (alipay-virity alipay request)
               (timbre/info "支付宝发来的消息校验处理失败:" request))
           (or (from-alipay? alipay (get-in request [:params :notify_id]))
               (timbre/info "不是支付宝发来的消息:" request)))
        (hander (fake-anti-forgery request))
        (not-found "支付宝发来的请求参数有问题"))
      (hander request))))
