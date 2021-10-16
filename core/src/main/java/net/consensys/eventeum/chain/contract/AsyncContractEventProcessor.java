package net.consensys.eventeum.chain.contract;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.consensys.eventeum.chain.service.BlockchainService;
import net.consensys.eventeum.chain.service.domain.Block;
import net.consensys.eventeum.chain.util.BloomFilterUtil;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.event.filter.ContractEventFilter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @Title:AsyncContractEventProcessor
 * @auther: kakapo911
 * @date: 2021/10/157:45 PM
 */
@Component
@Slf4j
@AllArgsConstructor
public class AsyncContractEventProcessor{

    private List<ContractEventListener> contractEventListeners;

    @Async("taskExecutor")
    public void processLogsForFilter(ContractEventFilter filter,
                                     Block block,
                                     BlockchainService blockchainService,
                                     List<ContractEventListener> contractEventListeners,
                                     CountDownLatch countDownLatch) {
        this.contractEventListeners = contractEventListeners;

        log.info("Thread:[{}] start processing contract:[{}]--event:[{}] event",Thread.currentThread().getName(),filter.getContractAddress(),filter.getEventSpecification().getEventName());
        if (block.getNodeName().equals(filter.getNode())
                && isEventFilterInBloomFilter(filter, block.getLogsBloom())) {
            blockchainService
                    .getEventsForFilter(filter, block.getNumber())
                    .forEach(event -> {
                        event.setTimestamp(block.getTimestamp());
                        triggerListeners(event);
                    });
        }
        countDownLatch.countDown();
        log.info("Thread:[{}] end processing contract:[{}]--event:[{}] event",Thread.currentThread().getName(),filter.getContractAddress(),filter.getEventSpecification().getEventName());
    }

    private boolean isEventFilterInBloomFilter(ContractEventFilter filter, String logsBloom) {
        final BloomFilterUtil.BloomFilterBits bloomBits = BloomFilterUtil.getBloomBits(filter);

        return BloomFilterUtil.bloomFilterMatch(logsBloom, bloomBits);
    }

    private void triggerListeners(ContractEventDetails contractEvent) {
        contractEventListeners.forEach(
                listener -> triggerListener(listener, contractEvent));
    }

    private void triggerListener(ContractEventListener listener, ContractEventDetails contractEventDetails) {
        try {
            listener.onEvent(contractEventDetails);
        } catch (Throwable t) {
            log.error(String.format(
                    "An error occurred when processing contractEvent with id %s", contractEventDetails.getId()), t);
            throw t;
        }
    }
}
