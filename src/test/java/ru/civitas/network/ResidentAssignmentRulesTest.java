package ru.civitas.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentAssignmentRulesTest {
    @Test
    void builderAssignmentIsRejectedWhileResidentCarriesLogs() {
        String error = ResidentAssignmentRules.cargoErrorForBuilder(8);

        assertEquals(
                "Нельзя назначить строителем: житель несёт 8 брёвен. Сначала дождись доставки груза на склад.",
                error
        );
    }

    @Test
    void builderAssignmentIsAllowedWhenThereIsNoCargo() {
        assertNull(ResidentAssignmentRules.cargoErrorForBuilder(0));
        assertNull(ResidentAssignmentRules.cargoErrorForBuilder(-1));
    }

    @Test
    void lumberjackMayKeepHisCurrentRoleWhileDeliveringLogs() {
        assertNull(ResidentAssignmentRules.cargoErrorForLumberjack("lumberjack", 8));
    }

    @Test
    void leftoverCargoBlocksRoleChangeToLumberjack() {
        String error = ResidentAssignmentRules.cargoErrorForLumberjack("builder", 8);

        assertTrue(error.contains("8 брёвен"));
        assertTrue(error.contains("текущая роль не"));
    }

    @Test
    void lumberjackAssignmentIsAllowedWithoutCargo() {
        assertNull(ResidentAssignmentRules.cargoErrorForLumberjack("builder", 0));
    }
}
