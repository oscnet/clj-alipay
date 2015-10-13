(defproject clj-alipay "0.1.3-snapshot"
  :description "支付宝即时到账交易接口(create_direct_pay_by_user)"
  :url "http://www.zjpjhx.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-http "2.0.0"]
                 [hiccup "1.0.5"]
                 [crypto-random "1.2.0"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [lib-noir "0.9.9"]]
  :profiles {:test {:dependencies [[ring/ring-mock "0.2.0"]
                                   [clj-http-fake "1.0.1"]]}})
