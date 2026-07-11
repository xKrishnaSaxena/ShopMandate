package com.shopmandate.net

/**
 * Payee details for the real UPI deep link (upi://pay).
 *
 * 👉 [PAYEE_VPA] ko apne real UPI ID (VPA) se replace karo — isi account pe paisa jayega.
 *    Jab tak yeh placeholder hai, UPI app "invalid UPI ID" dikha sakta hai.
 */
object UpiConfig {
    const val PAYEE_VPA = "yourname@upi"
    const val PAYEE_NAME = "ShopMandate"
}
