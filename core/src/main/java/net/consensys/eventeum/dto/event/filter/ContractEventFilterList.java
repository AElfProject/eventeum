package net.consensys.eventeum.dto.event.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigInteger;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;
@Document
@Entity
@Data
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractEventFilterList {
    private BigInteger startBlock;
    @ElementCollection
    private List<ContractEventFilter> filters = new ArrayList<>();
}
