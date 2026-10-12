package com.orbitcrm.deal.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.model.PicklistOption;

/**
 * Defines the Deals (opportunities) record module (PRD sections 13-14).
 *
 * <p>Purpose: register the {@code deals} {@link com.orbitcrm.metadata.model.ModuleDefinition} with the generic record
 * engine. The definition declares:
 * <ul>
 *   <li>record numbers {@code DEAL-n}, title "name", subtitle company and stage;</li>
 *   <li>fields: name, company and contact (lookups), amount and currency (the tenant currency, read-only), pipeline
 *   and stage (reference fields resolved by {@link PipelineReferenceResolvers}), probability (defaults to the stage's
 *   probability, can be overridden), weighted amount (read-only, computed by the database as amount x probability),
 *   expected close date, source, next step, status (open / won / lost, read-only), closed at, lost reason, source
 *   lead (read-only) and description;</li>
 *   <li>the picklist {@code deal_lost_reason}.</li>
 * </ul>
 * Stage, probability, status and close date are kept consistent by {@code DealHooks}, and every stage move is
 * recorded in the stage history.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class DealModule {

    public static final String KEY = "deals";
    public static final String REF_PIPELINE = "pipeline";
    public static final String REF_STAGE = "stage";

    @Bean
    ModuleDefinition dealModuleDefinition() {
        return ModuleDefinition.builder(KEY)
                .labels("Deal", "Deals")
                .recordPrefix("DEAL")
                .icon("handshake")
                .title("name")
                .subtitle("companyId", "stageId")
                .picklist(PicklistDefinition.of("deal_lost_reason", "Lost reason",
                        PicklistOption.of("price", "Price"),
                        PicklistOption.of("competitor", "Lost to competitor"),
                        PicklistOption.of("no_budget", "No budget"),
                        PicklistOption.of("no_decision", "No decision / went dark"),
                        PicklistOption.of("timing", "Timing"),
                        PicklistOption.of("features", "Missing features"),
                        PicklistOption.of("other", "Other")))
                .field(FieldDefinition.of("name", "Deal name", FieldType.TEXT).required().searchable().listDefault())
                .field(FieldDefinition.of("companyId", "Company", FieldType.LOOKUP).lookup("companies").listDefault()
                        .searchable())
                .field(FieldDefinition.of("contactId", "Contact", FieldType.LOOKUP).lookup("contacts"))
                .field(FieldDefinition.of("amount", "Amount", FieldType.CURRENCY).min(0).listDefault())
                .field(FieldDefinition.of("currency", "Currency", FieldType.TEXT).maxLength(3).readOnly())
                .field(FieldDefinition.of("pipelineId", "Pipeline", FieldType.REFERENCE).reference(REF_PIPELINE)
                        .required())
                .field(FieldDefinition.of("stageId", "Stage", FieldType.REFERENCE).reference(REF_STAGE).required()
                        .listDefault())
                .field(FieldDefinition.of("probability", "Probability (%)", FieldType.PERCENT)
                        .help("Defaults to the stage probability; can be overridden per deal"))
                .field(FieldDefinition.of("weightedAmount", "Weighted amount", FieldType.CURRENCY).readOnly())
                .field(FieldDefinition.of("expectedCloseDate", "Expected close date", FieldType.DATE).listDefault())
                .field(FieldDefinition.of("leadSource", "Source", FieldType.PICKLIST).picklist("lead_source"))
                .field(FieldDefinition.of("nextStep", "Next step", FieldType.TEXT))
                .field(FieldDefinition.of("status", "Status", FieldType.PICKLIST).readOnly().options(List.of(
                        PicklistOption.of("open", "Open"), PicklistOption.of("won", "Won"),
                        PicklistOption.of("lost", "Lost"))))
                .field(FieldDefinition.of("closedAt", "Closed at", FieldType.DATETIME).readOnly())
                .field(FieldDefinition.of("lostReason", "Lost reason", FieldType.PICKLIST).picklist("deal_lost_reason"))
                .field(FieldDefinition.of("leadId", "Source lead", FieldType.LOOKUP).lookup("leads").readOnly())
                .field(FieldDefinition.of("description", "Description", FieldType.TEXTAREA))
                .build();
    }
}
