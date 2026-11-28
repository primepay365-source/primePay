package com.primepay.reports;

import com.primepay.common.api.ApiResponse;
import com.primepay.common.web.CsvWriter;
import com.primepay.security.CurrentActor;
import com.primepay.user.AccountRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;
    private final CurrentActor currentActor;

    public ReportController(ReportService reports, CurrentActor currentActor) {
        this.reports = reports;
        this.currentActor = currentActor;
    }

    @GetMapping("/dashboard")
    public ApiResponse<ReportService.Dashboard> dashboard() {
        return ApiResponse.ok(reports.dashboard(currentActor.require()));
    }

    @GetMapping("/reconciliation")
    public ApiResponse<ReportService.Reconciliation> reconciliation() {
        currentActor.requireRole(AccountRole.ADMIN);
        return ApiResponse.ok(reports.reconcile());
    }

    @GetMapping("/{type}")
    public ApiResponse<ReportService.Report> report(@PathVariable String type,
                                                    @RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(required = false) String q) {
        return ApiResponse.ok(reports.report(currentActor.require(), type, from, to, q));
    }

    @GetMapping(value = "/{type}/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(required = false) String q) {
        ReportService.Report report = reports.report(currentActor.require(), type, from, to, q);
        CsvWriter csv = new CsvWriter(report.columns().stream().map(ReportService.Column::label).toArray(String[]::new));
        report.rows().forEach(row -> csv.row(report.columns().stream().map(c -> row.get(c.key())).toArray()));
        return csv.download("primepay-" + report.type() + "-report.csv");
    }
}
