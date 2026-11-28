package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.billing.dto.InvoiceAlreadyPaidRequest;
import com.psymate.shared.problem.FieldProblem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Règles pures de l'émission (M15-09) : date d'émission, paiement à l'émission. */
class InvoiceIssueServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);

  @Test
  void anIssueDateIsNeitherInTheFutureNorBeforeTheLastIssuedOfItsSeries() {
    assertThat(InvoiceIssueService.issueDateProblems(TODAY, TODAY, null)).isEmpty();
    assertThat(InvoiceIssueService.issueDateProblems(TODAY.minusDays(30), TODAY, null)).isEmpty();
    assertThat(InvoiceIssueService.issueDateProblems(TODAY.minusDays(2), TODAY, TODAY.minusDays(2)))
        .isEmpty();
    assertThat(InvoiceIssueService.issueDateProblems(TODAY.plusDays(1), TODAY, null))
        .extracting(FieldProblem::field, FieldProblem::code)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("issueDate", "OUT_OF_RANGE"));
    assertThat(InvoiceIssueService.issueDateProblems(TODAY.minusDays(3), TODAY, TODAY.minusDays(2)))
        .extracting(FieldProblem::field, FieldProblem::code)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("issueDate", "INVALID_VALUE"));
  }

  @Test
  void alreadyPaidNeedsAnAcceptedModeOrOtherAndNoFutureDate() {
    List<String> accepted = List.of("transfer", "cheque");
    assertThat(InvoiceIssueService.alreadyPaidProblems(null, accepted, TODAY)).isEmpty();
    assertThat(
            InvoiceIssueService.alreadyPaidProblems(
                new InvoiceAlreadyPaidRequest("cheque", TODAY, null), accepted, TODAY))
        .isEmpty();
    assertThat(
            InvoiceIssueService.alreadyPaidProblems(
                new InvoiceAlreadyPaidRequest("other", TODAY.minusDays(5), "x"), accepted, TODAY))
        .isEmpty();
    assertThat(
            InvoiceIssueService.alreadyPaidProblems(
                new InvoiceAlreadyPaidRequest("cash", TODAY.plusDays(1), null), accepted, TODAY))
        .extracting(FieldProblem::field, FieldProblem::code)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("alreadyPaid.mode", "MODE_NOT_ACCEPTED"),
            org.assertj.core.groups.Tuple.tuple("alreadyPaid.receivedOn", "OUT_OF_RANGE"));
  }
}
