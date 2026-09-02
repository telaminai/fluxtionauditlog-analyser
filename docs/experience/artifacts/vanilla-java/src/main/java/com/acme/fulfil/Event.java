package com.acme.fulfil;

import java.util.*;

public abstract class Event {
    public final long timestampMs;
    protected List<String> rawFields;

    public Event(long timestampMs, List<String> rawFields) {
        this.timestampMs = timestampMs;
        this.rawFields = rawFields;
    }

    public static Event parse(String line) {
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
            return null;
        }
        String[] parts = line.split(",");
        if (parts.length == 0) return null;

        String type = parts[0].trim();
        List<String> fields = Arrays.asList(parts);

        return switch (type) {
            case "PRODUCT" -> new ProductEvent(fields);
            case "CUSTOMER" -> new CustomerEvent(fields);
            case "CARRIER" -> new CarrierEvent(fields);
            case "RECEIPT" -> new ReceiptEvent(fields);
            case "ADJUST" -> new AdjustEvent(fields);
            case "COUNT" -> new CountEvent(fields);
            case "ORDER" -> new OrderEvent(fields);
            case "AMEND" -> new AmendEvent(fields);
            case "CANCEL" -> new CancelEvent(fields);
            case "PAID" -> new PaidEvent(fields);
            case "PAYFAIL" -> new PayfailEvent(fields);
            case "PICKSTART" -> new PickstartEvent(fields);
            case "PICKDONE" -> new PickdoneEvent(fields);
            case "DISPATCH" -> new DispatchEvent(fields);
            case "DELIVERED" -> new DeliveredEvent(fields);
            default -> null;
        };
    }

    // Reference data events
    public static class ProductEvent extends Event {
        public String sku;
        public double unitPrice;
        public boolean hazardous;

        public ProductEvent(List<String> fields) {
            super(0, fields);
            this.sku = fields.get(1).trim();
            this.unitPrice = Double.parseDouble(fields.get(2).trim());
            this.hazardous = Boolean.parseBoolean(fields.get(3).trim());
        }
    }

    public static class CustomerEvent extends Event {
        public String customerId;
        public String tier;
        public double creditLimit;

        public CustomerEvent(List<String> fields) {
            super(0, fields);
            this.customerId = fields.get(1).trim();
            this.tier = fields.get(2).trim();
            this.creditLimit = Double.parseDouble(fields.get(3).trim());
        }
    }

    public static class CarrierEvent extends Event {
        public String carrierId;
        public double maxWeightKg;
        public boolean handlesHazardous;

        public CarrierEvent(List<String> fields) {
            super(0, fields);
            this.carrierId = fields.get(1).trim();
            this.maxWeightKg = Double.parseDouble(fields.get(2).trim());
            this.handlesHazardous = Boolean.parseBoolean(fields.get(3).trim());
        }
    }

    // Stock events
    public static class ReceiptEvent extends Event {
        public String sku;
        public long quantity;

        public ReceiptEvent(List<String> fields) {
            super(Long.parseLong(fields.get(3).trim()), fields);
            this.sku = fields.get(1).trim();
            this.quantity = Long.parseLong(fields.get(2).trim());
        }
    }

    public static class AdjustEvent extends Event {
        public String sku;
        public long delta;

        public AdjustEvent(List<String> fields) {
            super(Long.parseLong(fields.get(3).trim()), fields);
            this.sku = fields.get(1).trim();
            this.delta = Long.parseLong(fields.get(2).trim());
        }
    }

    public static class CountEvent extends Event {
        public String sku;
        public long countedQuantity;

        public CountEvent(List<String> fields) {
            super(Long.parseLong(fields.get(3).trim()), fields);
            this.sku = fields.get(1).trim();
            this.countedQuantity = Long.parseLong(fields.get(2).trim());
        }
    }

    // Order events
    public static class OrderEvent extends Event {
        public String orderId;
        public String customerId;
        public String sku;
        public long quantity;

        public OrderEvent(List<String> fields) {
            super(Long.parseLong(fields.get(5).trim()), fields);
            this.orderId = fields.get(1).trim();
            this.customerId = fields.get(2).trim();
            this.sku = fields.get(3).trim();
            this.quantity = Long.parseLong(fields.get(4).trim());
        }
    }

    public static class AmendEvent extends Event {
        public String orderId;
        public long newQuantity;

        public AmendEvent(List<String> fields) {
            super(Long.parseLong(fields.get(3).trim()), fields);
            this.orderId = fields.get(1).trim();
            this.newQuantity = Long.parseLong(fields.get(2).trim());
        }
    }

    public static class CancelEvent extends Event {
        public String orderId;

        public CancelEvent(List<String> fields) {
            super(Long.parseLong(fields.get(2).trim()), fields);
            this.orderId = fields.get(1).trim();
        }
    }

    // Payment events
    public static class PaidEvent extends Event {
        public String orderId;
        public double amount;

        public PaidEvent(List<String> fields) {
            super(Long.parseLong(fields.get(3).trim()), fields);
            this.orderId = fields.get(1).trim();
            this.amount = Double.parseDouble(fields.get(2).trim());
        }
    }

    public static class PayfailEvent extends Event {
        public String orderId;

        public PayfailEvent(List<String> fields) {
            super(Long.parseLong(fields.get(2).trim()), fields);
            this.orderId = fields.get(1).trim();
        }
    }

    // Fulfilment events
    public static class PickstartEvent extends Event {
        public String orderId;

        public PickstartEvent(List<String> fields) {
            super(Long.parseLong(fields.get(2).trim()), fields);
            this.orderId = fields.get(1).trim();
        }
    }

    public static class PickdoneEvent extends Event {
        public String orderId;

        public PickdoneEvent(List<String> fields) {
            super(Long.parseLong(fields.get(2).trim()), fields);
            this.orderId = fields.get(1).trim();
        }
    }

    public static class DispatchEvent extends Event {
        public String orderId;
        public String carrierId;
        public double weightKg;

        public DispatchEvent(List<String> fields) {
            super(Long.parseLong(fields.get(4).trim()), fields);
            this.orderId = fields.get(1).trim();
            this.carrierId = fields.get(2).trim();
            this.weightKg = Double.parseDouble(fields.get(3).trim());
        }
    }

    public static class DeliveredEvent extends Event {
        public String orderId;

        public DeliveredEvent(List<String> fields) {
            super(Long.parseLong(fields.get(2).trim()), fields);
            this.orderId = fields.get(1).trim();
        }
    }
}
