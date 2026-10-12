package com.orbitcrm.lead.hook;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.orbitcrm.lead.config.LeadModule;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.hook.RecordHooks;
import com.orbitcrm.record.model.ChangeOrigin;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Record hooks that protect the lead lifecycle.
 *
 * <p>Purpose: keep conversion history trustworthy. The system status "converted" can only be reached through the
 * conversion flow (setting it manually fails with {@code USE_CONVERT_ACTION}), and once a lead is converted it is
 * frozen: any further update fails with {@code 409 LEAD_CONVERTED}. Writes made by the conversion itself
 * ({@code ChangeOrigin.CONVERSION}) are allowed.
 *
 * @author Abhay
 */
@Component
public class LeadHooks implements RecordHooks {

    @Override
    public String moduleKey() {
        return LeadModule.KEY;
    }

    @Override
    public void beforeCreate(HookContext context, Map<String, Object> values) {
        if (LeadModule.STATUS_CONVERTED.equals(values.get("status")) && !isConversion(context)) {
            throw convertStatusNotAllowed();
        }
    }

    @Override
    public void beforeUpdate(HookContext context, RecordView existing, Map<String, Object> changes) {
        if (isConversion(context)) {
            return;
        }
        if (LeadModule.STATUS_CONVERTED.equals(existing.value("status"))) {
            throw ApiException.conflict("LEAD_CONVERTED", "Converted leads are read-only");
        }
        if (LeadModule.STATUS_CONVERTED.equals(changes.get("status"))) {
            throw convertStatusNotAllowed();
        }
    }

    private static boolean isConversion(HookContext context) {
        return context.options().origin() == ChangeOrigin.CONVERSION;
    }

    private static ApiException convertStatusNotAllowed() {
        return ApiException.validation("status", "USE_CONVERT_ACTION",
                "Use the convert action to convert a lead");
    }
}
