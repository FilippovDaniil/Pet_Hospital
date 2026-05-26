package com.hospital.controller;

import com.hospital.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Платёжный шлюз Альфа Банк")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping(value = "/callback", produces = "text/html;charset=UTF-8")
    @Operation(summary = "Callback от Альфа Банка после успешной оплаты (публичный)")
    public String paymentCallback(@RequestParam("orderId") String alfaOrderId) {
        String result = paymentService.confirmPayment(alfaOrderId);
        return switch (result) {
            case "paid"      -> successHtml();
            case "failed"    -> failHtml("Платёж был отклонён банком. Попробуйте другую карту.");
            case "pending"   -> failHtml("Оплата не завершена. Пожалуйста, попробуйте снова.");
            default          -> failHtml("Заказ не найден. Обратитесь в службу поддержки.");
        };
    }

    @GetMapping(value = "/fail", produces = "text/html;charset=UTF-8")
    @Operation(summary = "Страница отказа от оплаты (публичный)")
    public String paymentFail(@RequestParam(value = "orderId", required = false) String alfaOrderId) {
        if (alfaOrderId != null) {
            paymentService.confirmPayment(alfaOrderId);
        }
        return failHtml("Оплата не была завершена. Вы можете попробовать снова.");
    }

    private String successHtml() {
        return "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\">" +
               "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
               "<title>Оплата успешна</title>" +
               "<style>" +
               "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;" +
               "background:#f0fdf4;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;}" +
               ".card{background:#fff;border-radius:16px;padding:48px 40px;text-align:center;" +
               "max-width:440px;width:90%;box-shadow:0 4px 24px rgba(0,0,0,.08);}" +
               ".icon{font-size:64px;margin-bottom:16px;}" +
               "h1{color:#16a34a;font-size:1.5rem;margin-bottom:8px;}" +
               "p{color:#6b7280;line-height:1.6;margin-bottom:28px;}" +
               "a{display:inline-block;background:#1d4ed8;color:#fff;text-decoration:none;" +
               "padding:12px 32px;border-radius:8px;font-weight:600;}" +
               "a:hover{background:#1e3a8a;}" +
               "</style></head><body>" +
               "<div class=\"card\">" +
               "<div class=\"icon\">✅</div>" +
               "<h1>Оплата прошла успешно</h1>" +
               "<p>Ваша заявка подтверждена. Мы свяжемся с вами для уточнения даты и времени приёма.</p>" +
               "<a href=\"/account.html\">Перейти к моим заказам</a>" +
               "</div></body></html>";
    }

    private String failHtml(String reason) {
        return "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\">" +
               "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
               "<title>Ошибка оплаты</title>" +
               "<style>" +
               "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;" +
               "background:#fef2f2;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;}" +
               ".card{background:#fff;border-radius:16px;padding:48px 40px;text-align:center;" +
               "max-width:440px;width:90%;box-shadow:0 4px 24px rgba(0,0,0,.08);}" +
               ".icon{font-size:64px;margin-bottom:16px;}" +
               "h1{color:#dc2626;font-size:1.5rem;margin-bottom:8px;}" +
               "p{color:#6b7280;line-height:1.6;margin-bottom:28px;}" +
               ".btns{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;}" +
               "a{display:inline-block;color:#fff;text-decoration:none;" +
               "padding:12px 24px;border-radius:8px;font-weight:600;}" +
               ".btn-primary{background:#1d4ed8;}" +
               ".btn-primary:hover{background:#1e3a8a;}" +
               ".btn-secondary{background:#6b7280;}" +
               ".btn-secondary:hover{background:#4b5563;}" +
               "</style></head><body>" +
               "<div class=\"card\">" +
               "<div class=\"icon\">❌</div>" +
               "<h1>Оплата не прошла</h1>" +
               "<p>" + reason + "</p>" +
               "<div class=\"btns\">" +
               "<a href=\"/client.html#services\" class=\"btn-primary\">Попробовать снова</a>" +
               "<a href=\"/account.html\" class=\"btn-secondary\">Мои заказы</a>" +
               "</div></div></body></html>";
    }
}
