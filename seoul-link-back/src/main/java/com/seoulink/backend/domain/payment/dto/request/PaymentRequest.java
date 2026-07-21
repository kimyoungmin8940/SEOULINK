package com.seoulink.backend.domain.payment.dto.request;

public class PaymentRequest {
    private Long userId;
    private String itemName;
    private int amount;

    public Long getUserId() { return userId; }
    public String getItemName() { return itemName; }
    public int getAmount() { return amount; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setAmount(int amount) { this.amount = amount; }
}