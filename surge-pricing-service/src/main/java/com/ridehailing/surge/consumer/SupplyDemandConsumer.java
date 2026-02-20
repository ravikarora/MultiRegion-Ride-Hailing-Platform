package com.ridehailing.surge.consumer;

import com.ridehailing.shared.events.SupplyDemandSnapshotEvent;
import com.ridehailing.shared.util.KafkaTopics;
import com.ridehailing.surge.metrics.SurgeMetrics;
import com.ridehailing.surge.service.SurgeCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyDemandConsumer {

    private final SurgeCalculatorService surgeCalculatorService;
    private final SurgeMetrics surgeMetrics;

    @KafkaListener(
            topics = KafkaTopics.SUPPLY_DEMAND_SNAPSHOT,
            groupId = "surge-pricing-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(SupplyDemandSnapshotEvent event, Acknowledgment ack) {
        try {
            surgeCalculatorService.processSnapshot(event);
            surgeMetrics.recordSnapshotProcessed();
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process supply/demand snapshot for cell {}: {}",
                    event.getGeoCell(), e.getMessage(), e);
            // Do NOT ack — let Kafka retry
        }
    }
}
