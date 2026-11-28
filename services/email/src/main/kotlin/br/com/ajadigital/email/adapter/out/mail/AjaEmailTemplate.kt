package br.com.ajadigital.email.adapter.out.mail

import org.springframework.web.util.HtmlUtils

internal object AjaEmailTemplate {
    fun render(
        title: String,
        body: String,
        actionLabel: String,
        actionUrl: String,
    ): String {
        val safeBody = escape(body).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>")
        return """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:#faf4fc;color:#3e154c;font-family:'Segoe UI',Arial,sans-serif;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#faf4fc;padding:28px 12px;"><tr><td align="center">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;">
<tr><td style="height:6px;background:#8f2db4;font-size:0;line-height:0;">&nbsp;</td></tr>
<tr><td style="padding:28px 28px 12px;"><table role="presentation" cellspacing="0" cellpadding="0"><tr>
<td style="vertical-align:middle;padding-right:10px;"><img src="cid:aja-symbol" width="40" height="40" alt="" style="display:block;width:40px;height:40px;border:0;"></td>
<td style="vertical-align:middle;color:#3e154c;font-size:19px;font-weight:700;font-family:'Trebuchet MS','Segoe UI',Arial,sans-serif;">AJA <span style="font-size:14px;font-weight:400;">digital</span></td>
</tr></table></td></tr>
<tr><td style="padding:8px 28px 4px;"><h1 style="margin:0;color:#3e154c;font-size:25px;line-height:1.3;font-weight:700;overflow-wrap:anywhere;word-break:break-word;">${escape(
            title,
        )}</h1></td></tr>
<tr><td style="padding:12px 28px 24px;color:#44364a;font-size:16px;line-height:1.6;overflow-wrap:anywhere;word-break:break-word;">$safeBody</td></tr>
<tr><td align="center" style="padding:0 28px 26px;"><a href="${escape(
            actionUrl,
        )}" style="display:inline-block;padding:13px 24px;border-radius:8px;background:#75218d;color:#ffffff;text-decoration:none;font-size:16px;line-height:1.3;font-weight:700;">${escape(
            actionLabel,
        )}</a></td></tr>
<tr><td style="padding:0 28px 26px;color:#625a65;font-size:13px;line-height:1.5;overflow-wrap:anywhere;word-break:break-word;">Se o botão não funcionar, copie este endereço no navegador:<br><a href="${escape(
            actionUrl,
        )}" style="color:#75218d;">${escape(actionUrl)}</a></td></tr>
<tr><td style="padding:18px 28px;background:#f2e5f7;color:#625a65;font-size:12px;line-height:1.5;">Mensagem automática da AJA digital.</td></tr>
</table></td></tr></table>
</body></html>"""
    }

    private fun escape(value: String): String = HtmlUtils.htmlEscape(value, "UTF-8")
}
