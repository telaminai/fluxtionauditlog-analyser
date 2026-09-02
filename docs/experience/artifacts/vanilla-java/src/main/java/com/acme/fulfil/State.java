package com.acme.fulfil;

import java.util.*;

public class State {
    // Reference data - R11 tracks previous values to detect changes
    Map<String, ProductData> products = new HashMap<>();
    Map<String, CustomerData> customers = new HashMap<>();
    Map<String, CarrierData> carriers = new HashMap<>();

    // Stock tracking - R1
    Map<String, Long> onHandStock = new HashMap<>();

    // Order tracking - R2
    static class OrderData {
        String orderId;
        String customerId;
        String sku;
        long quantity;
        String state; // "OPEN" or "CANCELLED"
        long releaseTimestampMs = -1; // timestamp in ms when order was released (for R9)
        boolean hasEverBeenReleasable = false; // for R6 edge detection

        OrderData(String orderId, String customerId, String sku, long quantity) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.sku = sku;
            this.quantity = quantity;
            this.state = "OPEN";
        }
    }

    Map<String, OrderData> orders = new HashMap<>();

    // R3 - paid amount per order
    Map<String, Double> paidAmount = new HashMap<>();

    // Reference data classes
    static class ProductData {
        String sku;
        double unitPrice;
        boolean hazardous;

        ProductData(String sku, double unitPrice, boolean hazardous) {
            this.sku = sku;
            this.unitPrice = unitPrice;
            this.hazardous = hazardous;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProductData)) return false;
            ProductData that = (ProductData) o;
            return Double.compare(that.unitPrice, unitPrice) == 0 &&
                    hazardous == that.hazardous &&
                    Objects.equals(sku, that.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku, unitPrice, hazardous);
        }
    }

    static class CustomerData {
        String customerId;
        String tier;
        double creditLimit;

        CustomerData(String customerId, String tier, double creditLimit) {
            this.customerId = customerId;
            this.tier = tier;
            this.creditLimit = creditLimit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CustomerData)) return false;
            CustomerData that = (CustomerData) o;
            return Double.compare(that.creditLimit, creditLimit) == 0 &&
                    Objects.equals(customerId, that.customerId) &&
                    Objects.equals(tier, that.tier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, tier, creditLimit);
        }
    }

    static class CarrierData {
        String carrierId;
        double maxWeightKg;
        boolean handlesHazardous;

        CarrierData(String carrierId, double maxWeightKg, boolean handlesHazardous) {
            this.carrierId = carrierId;
            this.maxWeightKg = maxWeightKg;
            this.handlesHazardous = handlesHazardous;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CarrierData)) return false;
            CarrierData that = (CarrierData) o;
            return Double.compare(that.maxWeightKg, maxWeightKg) == 0 &&
                    handlesHazardous == that.handlesHazardous &&
                    Objects.equals(carrierId, that.carrierId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(carrierId, maxWeightKg, handlesHazardous);
        }
    }

    // R1 - on-hand stock
    long getOnHandStock(String sku) {
        return onHandStock.getOrDefault(sku, 0L);
    }

    void addStock(String sku, long amount) {
        onHandStock.put(sku, getOnHandStock(sku) + amount);
    }

    void setStock(String sku, long amount) {
        onHandStock.put(sku, amount);
    }

    // R2 - order state
    OrderData getOrder(String orderId) {
        return orders.get(orderId);
    }

    void createOrder(String orderId, String customerId, String sku, long quantity) {
        orders.put(orderId, new OrderData(orderId, customerId, sku, quantity));
        paidAmount.put(orderId, 0.0);
    }

    boolean isOrderOpen(String orderId) {
        OrderData order = getOrder(orderId);
        return order != null && "OPEN".equals(order.state);
    }

    boolean isOrderCancelled(String orderId) {
        OrderData order = getOrder(orderId);
        return order != null && "CANCELLED".equals(order.state);
    }

    void cancelOrder(String orderId) {
        OrderData order = getOrder(orderId);
        if (order != null) {
            order.state = "CANCELLED";
        }
    }

    void amendOrderQuantity(String orderId, long newQuantity) {
        OrderData order = getOrder(orderId);
        if (order != null && "OPEN".equals(order.state)) {
            order.quantity = newQuantity;
        }
    }

    // R3 - paid amount
    double getPaidAmount(String orderId) {
        return paidAmount.getOrDefault(orderId, 0.0);
    }

    void addPaidAmount(String orderId, double amount) {
        paidAmount.put(orderId, getPaidAmount(orderId) + amount);
    }

    // R4 - allocatable
    boolean isAllocatable(String orderId) {
        OrderData order = getOrder(orderId);
        if (order == null || !isOrderOpen(orderId)) return false;
        return getOnHandStock(order.sku) >= order.quantity;
    }

    // R5 - credit ok
    boolean isCreditOk(String orderId) {
        OrderData order = getOrder(orderId);
        if (order == null || !isOrderOpen(orderId)) return false;

        ProductData product = products.get(order.sku);
        if (product == null) return false;

        double price = order.quantity * product.unitPrice;
        double paid = getPaidAmount(orderId);

        // Either paid amount >= price
        if (paid >= price) return true;

        // Or customer is GOLD and price <= credit limit
        CustomerData customer = customers.get(order.customerId);
        if (customer != null && "GOLD".equals(customer.tier)) {
            return price <= customer.creditLimit;
        }

        return false;
    }

    // R6 - detect if order becomes releasable
    boolean isReleasable(String orderId) {
        return isAllocatable(orderId) && isCreditOk(orderId);
    }

    void markReleased(String orderId, long timestampMs) {
        OrderData order = getOrder(orderId);
        if (order != null) {
            order.releaseTimestampMs = timestampMs;
            order.hasEverBeenReleasable = true;
        }
    }

    // For reference data change detection (R11)
    boolean updateProduct(String sku, double unitPrice, boolean hazardous) {
        ProductData newData = new ProductData(sku, unitPrice, hazardous);
        ProductData oldData = products.get(sku);
        if (oldData != null && oldData.equals(newData)) {
            return false; // No change
        }
        products.put(sku, newData);
        return true; // Changed
    }

    boolean updateCustomer(String customerId, String tier, double creditLimit) {
        CustomerData newData = new CustomerData(customerId, tier, creditLimit);
        CustomerData oldData = customers.get(customerId);
        if (oldData != null && oldData.equals(newData)) {
            return false; // No change
        }
        customers.put(customerId, newData);
        return true; // Changed
    }

    boolean updateCarrier(String carrierId, double maxWeightKg, boolean handlesHazardous) {
        CarrierData newData = new CarrierData(carrierId, maxWeightKg, handlesHazardous);
        CarrierData oldData = carriers.get(carrierId);
        if (oldData != null && oldData.equals(newData)) {
            return false; // No change
        }
        carriers.put(carrierId, newData);
        return true; // Changed
    }

    ProductData getProduct(String sku) {
        return products.get(sku);
    }

    CustomerData getCustomer(String customerId) {
        return customers.get(customerId);
    }

    CarrierData getCarrier(String carrierId) {
        return carriers.get(carrierId);
    }
}
