package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstructionTextTest {

    @Test void listFieldsKeepOneTrimmedItemPerLine() {
        assertThat(InstructionText.normalize(InstructionField.STEPS, "  Apply online \r\n\r\n\t2. Pay the fee\rCollect  "))
                .isEqualTo("Apply online\n2. Pay the fee\nCollect");
    }

    @Test void blankValuesClearTheField() {
        assertThat(InstructionText.normalize(InstructionField.FEE, "   \n ")).isNull();
        assertThat(InstructionText.normalize(InstructionField.REQUIRED_DOCUMENTS, "\n\n")).isNull();
        assertThat(InstructionText.normalize(InstructionField.FEE, null)).isNull();
    }

    @Test void textFieldsKeepParagraphsButDropControlCharacters() {
        assertThat(InstructionText.normalize(InstructionField.WHO_CAN_USE, " Adults\u0007\n\n\n\nResidents "))
                .isEqualTo("Adults\n\nResidents");
    }

    @Test void listFieldsRejectTooManyItems() {
        String items = "Item\n".repeat(InstructionField.MAX_LIST_ITEMS + 1);
        assertThatThrownBy(() -> InstructionText.normalize(InstructionField.REQUIRED_DOCUMENTS, items))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requiredDocuments");
    }

    @Test void fieldsRejectTextAboveTheirLimit() {
        assertThatThrownBy(() -> InstructionText.normalize(InstructionField.PROCESSING_TIME, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("processingTime");
    }
}
