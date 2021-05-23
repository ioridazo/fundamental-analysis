package github.com.ioridazo.fundanalyzer.web.controller;

import github.com.ioridazo.fundanalyzer.domain.service.AnalysisService;
import github.com.ioridazo.fundanalyzer.domain.service.ViewService;
import github.com.ioridazo.fundanalyzer.web.model.BetweenDateInputData;
import github.com.ioridazo.fundanalyzer.web.model.CodeInputData;
import github.com.ioridazo.fundanalyzer.web.model.DateInputData;
import github.com.ioridazo.fundanalyzer.web.model.IdInputData;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.util.Arrays;

@Controller
public class AnalysisController {

    private static final String REDIRECT_INDEX = "redirect:/fundanalyzer/v1/index";
    private static final String REDIRECT_CORPORATE = "redirect:/fundanalyzer/v1/corporate";

    private final AnalysisService analysisService;
    private final ViewService viewService;

    public AnalysisController(
            final AnalysisService analysisService,
            final ViewService viewService) {
        this.analysisService = analysisService;
        this.viewService = viewService;
    }

    /**
     * 指定提出日の書類をメインの一連処理をする
     *
     * @param fromDate 提出日
     * @param toDate   提出日
     * @return Index
     */
    @PostMapping("fundanalyzer/v1/document/analysis")
    public String doMain(final String fromDate, final String toDate) {
        analysisService.doMain(BetweenDateInputData.of(LocalDate.parse(fromDate), LocalDate.parse(toDate)));
        return REDIRECT_INDEX;
    }

    /**
     * 表示をアップデートする
     *
     * @return Index
     */
    @PostMapping("fundanalyzer/v1/update/view")
    public String updateView() {
        viewService.updateView();
        return REDIRECT_INDEX + "?message=updating";
    }

    /**
     * 指定提出日の書類を分析する
     *
     * @param date 提出日
     * @return Index
     */
    @PostMapping("fundanalyzer/v1/scrape/date")
    public String scrapeByDate(final String date) {
        analysisService.doByDate(DateInputData.of(LocalDate.parse(date)));
        return REDIRECT_INDEX;
    }

    /**
     * 指定書類IDを分析する
     *
     * @param documentId 書類ID（CSVで複数可能）
     * @return Index
     */
    @PostMapping("fundanalyzer/v1/scrape/id")
    public String scrapeById(final String documentId) {
        Arrays.stream(documentId.split(","))
                .filter(dId -> dId.length() == 8)
                .map(IdInputData::of)
                .forEach(analysisService::doById);
        return REDIRECT_INDEX;
    }

    /**
     * 指定日に提出した企業の株価を取得する
     *
     * @param fromDate 提出日
     * @param toDate   提出日
     * @return Index
     */
    @PostMapping("fundanalyzer/v1/import/stock/date")
    public String importStock(final String fromDate, final String toDate) {
        analysisService.importStock(BetweenDateInputData.of(LocalDate.parse(fromDate), LocalDate.parse(toDate)));
        return REDIRECT_INDEX;
    }

    /**
     * 企業の株価を取得する
     *
     * @param code 会社コード
     * @return BrandDetail
     */
    @PostMapping("fundanalyzer/v1/import/stock/code")
    public String importStock(final String code) {
        analysisService.importStock(CodeInputData.of(code));
        return REDIRECT_CORPORATE + "/" + code.substring(0, 4);
    }
}
