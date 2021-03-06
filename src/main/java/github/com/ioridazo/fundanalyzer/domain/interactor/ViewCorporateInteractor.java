package github.com.ioridazo.fundanalyzer.domain.interactor;

import github.com.ioridazo.fundanalyzer.client.log.Category;
import github.com.ioridazo.fundanalyzer.client.log.FundanalyzerLogClient;
import github.com.ioridazo.fundanalyzer.client.log.Process;
import github.com.ioridazo.fundanalyzer.client.slack.SlackClient;
import github.com.ioridazo.fundanalyzer.domain.domain.entity.transaction.FinancialStatementEntity;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.AnalysisResultSpecification;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.CompanySpecification;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.DocumentSpecification;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.FinancialStatementSpecification;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.StockSpecification;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.ViewSpecification;
import github.com.ioridazo.fundanalyzer.domain.usecase.ViewCorporateUseCase;
import github.com.ioridazo.fundanalyzer.domain.value.Company;
import github.com.ioridazo.fundanalyzer.domain.value.Document;
import github.com.ioridazo.fundanalyzer.domain.value.Stock;
import github.com.ioridazo.fundanalyzer.exception.FundanalyzerNotExistException;
import github.com.ioridazo.fundanalyzer.web.model.CodeInputData;
import github.com.ioridazo.fundanalyzer.web.model.DateInputData;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.CorporateViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.AnalysisResultViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.CompanyViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.CorporateDetailViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.FinancialStatementKeyViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.FinancialStatementViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.MinkabuViewModel;
import github.com.ioridazo.fundanalyzer.web.view.model.corporate.detail.StockPriceViewModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ViewCorporateInteractor implements ViewCorporateUseCase {

    private static final Logger log = LogManager.getLogger(ViewCorporateInteractor.class);

    private final AnalyzeInteractor analyzeInteractor;
    private final CompanySpecification companySpecification;
    private final DocumentSpecification documentSpecification;
    private final FinancialStatementSpecification financialStatementSpecification;
    private final AnalysisResultSpecification analysisResultSpecification;
    private final StockSpecification stockSpecification;
    private final ViewSpecification viewSpecification;
    private final SlackClient slackClient;

    @Value("${app.config.view.discount-rate}")
    BigDecimal configDiscountRate;
    @Value("${app.config.view.outlier-of-standard-deviation}")
    BigDecimal configOutlierOfStandardDeviation;
    @Value("${app.config.view.coefficient-of-variation}")
    BigDecimal configCoefficientOfVariation;
    @Value("${app.config.view.diff-forecast-stock}")
    BigDecimal configDiffForecastStock;
    @Value("${app.config.view.corporate.size}")
    int configCorporateSize;
    @Value("${app.config.scraping.document-type-code}")
    List<String> targetTypeCodes;

    public ViewCorporateInteractor(
            final AnalyzeInteractor analyzeInteractor,
            final CompanySpecification companySpecification,
            final DocumentSpecification documentSpecification,
            final FinancialStatementSpecification financialStatementSpecification,
            final AnalysisResultSpecification analysisResultSpecification,
            final StockSpecification stockSpecification,
            final ViewSpecification viewSpecification,
            final SlackClient slackClient) {
        this.analyzeInteractor = analyzeInteractor;
        this.companySpecification = companySpecification;
        this.documentSpecification = documentSpecification;
        this.financialStatementSpecification = financialStatementSpecification;
        this.analysisResultSpecification = analysisResultSpecification;
        this.stockSpecification = stockSpecification;
        this.viewSpecification = viewSpecification;
        this.slackClient = slackClient;
    }

    LocalDate nowLocalDate() {
        return LocalDate.now();
    }

    /**
     * メインビューを取得する
     *
     * @return 企業情報ビュー
     */
    @Override
    public List<CorporateViewModel> viewMain() {
        return viewSpecification.findAllCorporateView().stream()
                // not null
                .filter(cvm -> Objects.nonNull(cvm.getDiscountRate()))
                .filter(cvm -> Objects.nonNull(cvm.getStandardDeviation()))
                .filter(cvm -> Objects.nonNull(cvm.getLatestCorporateValue()))
                // 表示する提出日は一定期間のみ
                .filter(cvm -> cvm.getSubmitDate().isAfter(nowLocalDate().minusDays(configCorporateSize)))
                // 割安度が170%(外部設定値)以上を表示
                .filter(cvm -> cvm.getDiscountRate().compareTo(configDiscountRate) >= 0)
                // 標準偏差が外れ値となっていたら除外
                .filter(cvm -> cvm.getStandardDeviation().compareTo(configOutlierOfStandardDeviation) < 0)
                // 最新企業価値がマイナスの場合は除外
                .filter(cvm -> cvm.getLatestCorporateValue().compareTo(BigDecimal.ZERO) > 0)
                // 変動係数
                .filter(cvm -> {
                    if (Objects.isNull(cvm.getCoefficientOfVariation())) {
                        // 変動係数が存在しない
                        return true;
                    } else {
                        // 変動係数が0.6未満であること
                        if (cvm.getCoefficientOfVariation().compareTo(configCoefficientOfVariation) < 1) {
                            return true;
                        } else {
                            // 変動係数が0.6以上でも最新企業価値が高ければOK
                            return cvm.getLatestCorporateValue().compareTo(cvm.getAverageCorporateValue()) > -1;
                        }
                    }
                })
                // 予想株価
                .filter(cvb -> {
                    if (Objects.nonNull(cvb.getForecastStock())) {
                        // 株価予想が存在する場合、最新株価より高ければOK
                        return (cvb.getForecastStock().divide(cvb.getLatestStockPrice(), 3, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(1.1)) > 0)
                                && (cvb.getForecastStock().subtract(cvb.getLatestStockPrice()).compareTo(configDiffForecastStock) >= 0);
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * オールビューを取得する
     *
     * @return 企業情報ビュー
     */
    @Override
    public List<CorporateViewModel> viewAll() {
        return viewSpecification.findAllCorporateView();
    }

    /**
     * 割安度でソートする
     *
     * @return 企業情報ビュー
     */
    @Override
    public List<CorporateViewModel> sortByDiscountRate() {
        return viewSpecification.findAllCorporateView().stream()
                .filter(cvm -> Objects.nonNull(cvm.getDiscountRate()))
                .sorted(Comparator.comparing(CorporateViewModel::getDiscountRate).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 企業情報詳細ビューを取得する
     *
     * @param inputData 企業コード
     * @return 企業情報詳細ビュー
     */
    @Override
    public CorporateDetailViewModel viewCorporateDetail(final CodeInputData inputData) {
        final Company company = companySpecification.findCompanyByCode(inputData.getCode5()).orElseThrow(FundanalyzerNotExistException::new);
        final Stock stock = stockSpecification.findStock(company);

        final List<AnalysisResultViewModel> analysisResultList = analysisResultSpecification.displayTargetList(company, targetTypeCodes).stream()
                .map(AnalysisResultViewModel::of)
                .sorted(Comparator.comparing(AnalysisResultViewModel::getDocumentPeriod).reversed())
                .collect(Collectors.toList());

        final List<FinancialStatementViewModel> fsList = financialStatementSpecification.findByCompany(company).stream()
                .map(FinancialStatementKeyViewModel::of)
                .distinct()
                .map(key -> {
                    final List<FinancialStatementEntity> valueList = financialStatementSpecification.findByKeyPerCompany(company, key);
                    return FinancialStatementViewModel.of(
                            key.getSubmitDate(),
                            key,
                            financialStatementSpecification.parseBsSubjectValue(valueList),
                            financialStatementSpecification.parsePlSubjectValue(valueList)
                    );
                })
                .sorted(Comparator.comparing(FinancialStatementViewModel::getSubmitDate).reversed())
                .collect(Collectors.toList());

        return CorporateDetailViewModel.of(
                CompanyViewModel.of(company, stock),
                viewSpecification.findCorporateView(inputData),
                analysisResultList,
                fsList,
                stock.getMinkabuEntityList().stream()
                        .map(MinkabuViewModel::of)
                        .sorted(Comparator.comparing(MinkabuViewModel::getTargetDate).reversed())
                        .collect(Collectors.toList()),
                stock.getStockPriceEntityList().stream()
                        .map(StockPriceViewModel::of)
                        .sorted(Comparator.comparing(StockPriceViewModel::getTargetDate).reversed())
                        .collect(Collectors.toList())
        );
    }

    /**
     * すべてのビューを更新する
     */
    @Override
    public void updateView() {
        final long startTime = System.currentTimeMillis();
        final List<CorporateViewModel> viewModelList = companySpecification.allTargetCompanies().stream()
                .map(company -> viewSpecification.generateCorporateView(company, analyzeInteractor.calculateCorporateValue(company)))
                .collect(Collectors.toList());

        viewModelList.parallelStream().forEach(viewSpecification::upsert);

        slackClient.sendMessage("g.c.i.f.domain.service.ViewService.display.update.complete.corporate");

        log.info(FundanalyzerLogClient.toInteractorLogObject(
                "表示アップデートが正常に終了しました。",
                Category.VIEW,
                Process.UPDATE,
                System.currentTimeMillis() - startTime
        ));
    }

    /**
     * ビューを更新する
     *
     * @param inputData 企業コード
     */
    @Override
    public void updateView(final DateInputData inputData) {
        final long startTime = System.currentTimeMillis();
        final List<CorporateViewModel> viewModelList = documentSpecification.targetList(inputData).stream()
                .map(Document::getEdinetCode)
                .map(companySpecification::findCompanyByEdinetCode)
                .filter(Optional::isPresent)
                .map(company -> viewSpecification.generateCorporateView(company.get(), analyzeInteractor.calculateCorporateValue(company.get())))
                .collect(Collectors.toList());

        viewModelList.forEach(viewSpecification::upsert);

        log.info(FundanalyzerLogClient.toInteractorLogObject(
                MessageFormat.format("表示アップデートが正常に終了しました。対象提出日:{0}", inputData.getDate()),
                Category.VIEW,
                Process.UPDATE,
                System.currentTimeMillis() - startTime
        ));
    }
}
