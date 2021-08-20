/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.consensys.eventeum.service.sync;

import lombok.extern.slf4j.Slf4j;
import net.consensys.eventeum.chain.contract.ContractEventProcessor;
import net.consensys.eventeum.chain.service.block.BlockNumberService;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.event.filter.ContractEventFilter;
import net.consensys.eventeum.model.EventFilterSyncStatus;
import net.consensys.eventeum.model.LatestBlock;
import net.consensys.eventeum.model.SyncStatus;
import net.consensys.eventeum.repository.EventFilterSyncStatusRepository;
import net.consensys.eventeum.service.AsyncTaskService;
import net.consensys.eventeum.utils.ExecutorNameFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DefaultEventSyncService implements EventSyncService {

    private BlockNumberService blockNumberService;

    private EventRetriever eventRetriever;

    private EventFilterSyncStatusRepository syncStatusRepository;

    private ContractEventProcessor contractEventProcessor;

    private RetryTemplate retryTemplate;

    private AsyncTaskService asyncService;
    protected static final String BLOCK_EXECUTOR_NAME = "BLOCK";

    public DefaultEventSyncService(BlockNumberService blockNumberService, EventRetriever eventRetriever,
            EventFilterSyncStatusRepository syncStatusRepository, ContractEventProcessor contractEventProcessor,
            @Qualifier("eternalRetryTemplate") RetryTemplate retryTemplate,
            AsyncTaskService asyncService) {
        this.blockNumberService = blockNumberService;
        this.eventRetriever = eventRetriever;
        this.syncStatusRepository = syncStatusRepository;
        this.contractEventProcessor = contractEventProcessor;
        this.retryTemplate = retryTemplate;
        this.asyncService = asyncService;
    }

    @Override
    public void sync(List<ContractEventFilter> filters) {

        filters.forEach(filter -> retryTemplate.execute((context) -> {
            syncFilter(filter);
            return null;
        }));
    }

    @Override
    public void dynamicSync(ContractEventFilter filter) {
        retryTemplate.execute((context) -> {
            dynamicSyncFilter(filter);
            return null;
        });
    }

    private void dynamicSyncFilter(ContractEventFilter filter) {
        asyncService.execute(ExecutorNameFactory.build(BLOCK_EXECUTOR_NAME, filter.getNode()), () -> {            
            final BigInteger latestBlockNumber = blockNumberService.getTheLatestBlock(filter.getNode());
            if (latestBlockNumber.equals(BigInteger.ZERO)) {
                log.info("===Syncing latest block number is 0");
                return;
            }
            final BigInteger startBlock = filter.getStartBlock();
            log.info("===Syncing event filter with id {} from block {} to {}", filter.getId(), startBlock, latestBlockNumber);
            final EventFilterSyncStatus finalSyncStatus = getEventSyncStatus(filter.getId());
            if (startBlock.compareTo(latestBlockNumber) > 0) {
                finalSyncStatus.setSyncStatus(SyncStatus.SYNCED);
                log.info("===Syncing latest block is less than start block");
                return;
            }
            eventRetriever.retrieveEvents(filter, startBlock, latestBlockNumber,
                    (events) -> events.forEach(this::processEvent));
            finalSyncStatus.setSyncStatus(SyncStatus.SYNCED);
            syncStatusRepository.save(finalSyncStatus);
            log.info("Event filter with id {} has completed syncing", filter.getId());
        });
    }

    private void syncFilter(ContractEventFilter filter) {
        final Optional<EventFilterSyncStatus> syncStatus = syncStatusRepository.findById(filter.getId());

        if (!syncStatus.isPresent() || syncStatus.get().getSyncStatus() == SyncStatus.NOT_SYNCED) {
            final BigInteger startBlock = getStartBlock(filter, syncStatus);
            // Should sync to block start block number
            final BigInteger endBlock = blockNumberService.getStartBlockForNode(filter.getNode());

            log.info("Syncing event filter with id {} from block {} to {}", filter.getId(), startBlock, endBlock);

            eventRetriever.retrieveEvents(filter, startBlock, endBlock, (events) -> events.forEach(this::processEvent));

            final EventFilterSyncStatus finalSyncStatus = getEventSyncStatus(filter.getId());
            finalSyncStatus.setSyncStatus(SyncStatus.SYNCED);
            syncStatusRepository.save(finalSyncStatus);

            log.info("Event filter with id {} has completed syncing", filter.getId());

        } else {
            log.info("Event filter with id {} already synced", filter.getId());
        }
    }

    private void processEvent(ContractEventDetails contractEvent) {
        contractEventProcessor.processContractEvent(contractEvent);

        final EventFilterSyncStatus syncStatus = getEventSyncStatus(contractEvent.getFilterId());

        syncStatus.setLastBlockNumber(contractEvent.getBlockNumber());
        syncStatusRepository.save(syncStatus);
    }

    private EventFilterSyncStatus getEventSyncStatus(String id) {
        return syncStatusRepository.findById(id)
                .orElse(EventFilterSyncStatus.builder().filterId(id).syncStatus(SyncStatus.NOT_SYNCED).build());
    }

    private BigInteger getStartBlock(ContractEventFilter contractEventFilter,
            Optional<EventFilterSyncStatus> syncStatus) {

        if (syncStatus.isPresent() && syncStatus.get().getLastBlockNumber().compareTo(BigInteger.ZERO) > 0) {
            return syncStatus.get().getLastBlockNumber();
        }

        return contractEventFilter.getStartBlock();
    }
}
