# clj-alipay

支付宝即时到账交易接口(create_direct_pay_by_user)

## Usage

In your project.clj file:

[![Clojars Project](http://clojars.org/clj-alipay/latest-version.svg)](http://clojars.org/clj-alipay)

In your namespace declaration:

```clojure
(ns myapp.test.core
  (:require [clj-alipay.core :refer :all]
            [noir.util.middleware :refer [app-handler]]))
```

定义支付宝参数:

```clojure
(def alipay
  {:partner "2088...183"  ;签约的支付宝账号对应的支付宝唯一用户号。以 2088 开头的 16 位纯数字组成
   :key "rqverejzrs...zy9jm"  ;交易安全校验码
   :notify "/alipay/notify"   ;服务器异步通知页面路径
   :return "/alipay/return"   ;页面跳转同步通知页面路径
   })

```

自动转支付宝支付:

```clojure
(defn pay-page []
  (pay alipay {:out_trade_no "001010"
               :subject "商品名称"
               :total_fee "0.10"
               }))
```

支付后页面跳转同步通知页面处理:(请求参数说明请查看支付宝接口文档)

```clojure
(defn return
  [{:keys [out_trade_no trade_status buyer_id buyer_email gmt_payment notify_time total_fee] :as req}]
  (if (= "TRADE_SUCCESS" trade_status)
      (do ...)))
```

服务器异步通知处理,注意最后必须返回success:

```clojure
(defn notify
  [{:keys [out_trade_no trade_status buyer_id buyer_email gmt_payment notify_time total_fee] :as req}]
  (timbre/info "接收支付宝 notify 消息" req)
  ...
  "success")
```

define Compojure routes:

```clojure
(defroutes home-routes
  (GET "/pay" [] (pay-page))
  (POST "/alipay/notify" [] notify)
  (GET "/alipay/return" [] return)
  (GET "/" [] "ok"))
```

define ring app:

```clojure
(def app (app-handler
          [home-routes]
          :middleware [wrap-anti-forgery #(wrap-alipay % alipay)]
          :ring-defaults
          (assoc-in site-defaults [:security :anti-forgery] false)
          :access-rules []
          :formats [:json-kw :edn :transit-json]))
```
*如果使用了wrap-anti-forgery,为了能够接收到支付宝的notify通知,应该象如上示例中的middleware顺序使用.*

## License

Copyright © 2015 oscnet.
