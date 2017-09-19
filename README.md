accounts-akka
=================

Краткое описание 
------------
После запуска, rest сервис становиться доступен по url **http://localhost:8080**
Для того что бы посмотреть список счетов нужно послать GET запрос на url **http://localhost:8080/accounts**,
Для выполнения перевода между счетами нужно посылать POST запрос на **http://localhost:8080/payment/create**,
примерный вид тела запроса
```{"amount": 1, "from": 1, "to": 1}```

Запуск проекта
------------
Для запуска сервиса, нужно выполнить команду ```sbt clean run```

Тестирование
------------
Для запуска unit и e2e тестов нужно выполнить команду
```sbt clean test```

Проблемы
------------
* Протоколы обмена сообщениями между авторами, как и сама структура авторов не идельна

