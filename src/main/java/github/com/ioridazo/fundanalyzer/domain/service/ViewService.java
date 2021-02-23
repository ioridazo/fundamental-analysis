package github.com.ioridazo.fundanalyzer.domain.service;

import github.com.ioridazo.fundanalyzer.domain.dao.master.CompanyDao;
import github.com.ioridazo.fundanalyzer.domain.dao.master.IndustryDao;
import github.com.ioridazo.fundanalyzer.domain.dao.transaction.AnalysisResultDao;
import github.com.ioridazo.fundanalyzer.domain.dao.transaction.DocumentDao;
import github.com.ioridazo.fundanalyzer.domain.dao.transaction.MinkabuDao;
import github.com.ioridazo.fundanalyzer.domain.dao.transaction.StockPriceDao;
import github.com.ioridazo.fundanalyzer.domain.entity.master.Company;
import github.com.ioridazo.fundanalyzer.domain.entity.transaction.AnalysisResult;
import github.com.ioridazo.fundanalyzer.domain.entity.transaction.Document;
import github.com.ioridazo.fundanalyzer.domain.entity.transaction.StockPrice;
import github.com.ioridazo.fundanalyzer.domain.log.Category;
import github.com.ioridazo.fundanalyzer.domain.log.FundanalyzerLogClient;
import github.com.ioridazo.fundanalyzer.domain.log.Process;
import github.com.ioridazo.fundanalyzer.domain.logic.view.BrandDetailCorporateViewLogic;
import github.com.ioridazo.fundanalyzer.domain.logic.view.CorporateViewLogic;
import github.com.ioridazo.fundanalyzer.domain.logic.view.EdinetDetailViewLogic;
import github.com.ioridazo.fundanalyzer.domain.logic.view.EdinetListViewLogic;
import github.com.ioridazo.fundanalyzer.domain.logic.view.bean.BrandDetailViewBean;
import github.com.ioridazo.fundanalyzer.domain.logic.view.bean.CorporateViewBean;
import github.com.ioridazo.fundanalyzer.domain.logic.view.bean.CorporateViewDao;
import github.com.ioridazo.fundanalyzer.domain.logic.view.bean.EdinetDetailViewBean;
import github.com.ioridazo.fundanalyzer.domain.logic.view.bean.EdinetListViewBean;
import github.com.ioridazo.fundanalyzer.domain.logic.view.bean.EdinetListViewDao;
import github.com.ioridazo.fundanalyzer.proxy.slack.SlackProxy;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Log4j2
@Service
public class ViewService {

    private final SlackProxy slackProxy;
    private final CorporateViewLogic corporateViewLogic;
    private final EdinetListViewLogic edinetListViewLogic;
    private final BrandDetailCorporateViewLogic brandDetailCorporateViewLogic;
    private final EdinetDetailViewLogic edinetDetailViewLogic;
    private final IndustryDao industryDao;
    private final CompanyDao companyDao;
    private final DocumentDao documentDao;
    private final AnalysisResultDao analysisResultDao;
    private final StockPriceDao stockPriceDao;
    private final MinkabuDao minkabuDao;
    private final CorporateViewDao corporateViewDao;
    private final EdinetListViewDao edinetListViewDao;

    public ViewService(
            final SlackProxy slackProxy,
            final CorporateViewLogic corporateViewLogic,
            final EdinetListViewLogic edinetListViewLogic,
            final BrandDetailCorporateViewLogic brandDetailCorporateViewLogic,
            final EdinetDetailViewLogic edinetDetailViewLogic,
            final IndustryDao industryDao,
            final CompanyDao companyDao,
            final DocumentDao documentDao,
            final AnalysisResultDao analysisResultDao,
            final StockPriceDao stockPriceDao,
            final MinkabuDao minkabuDao,
            final CorporateViewDao corporateViewDao,
            final EdinetListViewDao edinetListViewDao) {
        this.slackProxy = slackProxy;
        this.corporateViewLogic = corporateViewLogic;
        this.edinetListViewLogic = edinetListViewLogic;
        this.brandDetailCorporateViewLogic = brandDetailCorporateViewLogic;
        this.edinetDetailViewLogic = edinetDetailViewLogic;
        this.industryDao = industryDao;
        this.companyDao = companyDao;
        this.documentDao = documentDao;
        this.analysisResultDao = analysisResultDao;
        this.stockPriceDao = stockPriceDao;
        this.minkabuDao = minkabuDao;
        this.corporateViewDao = corporateViewDao;
        this.edinetListViewDao = edinetListViewDao;
    }

    /**
     * 企業価値等を算出して一定以上を表示する
     *
     * @return 会社一覧
     */
    @NewSpan("ViewService.corporateView")
    public List<CorporateViewBean> corporateView() {
        return sortedCompanyList(getCorporateViewBeanList().stream()
                // not null
                .filter(cvb -> cvb.getDiscountRate() != null)
                // 割安度が120%以上を表示
                .filter(cvb -> cvb.getDiscountRate().compareTo(BigDecimal.valueOf(120)) > 0)
                // 標準偏差が外れ値となっていたら除外
                .filter(cvb -> cvb.getStandardDeviation().compareTo(BigDecimal.valueOf(10000)) < 0)
                // 最新企業価値がマイナスの場合は除外
                .filter(cvb -> cvb.getLatestCorporateValue().compareTo(BigDecimal.ZERO) > 0)
                // 変動係数
                .filter(cvb -> {
                    // 変動係数が0.6以下であること
                    if (cvb.getCoefficientOfVariation().compareTo(BigDecimal.valueOf(0.6)) < 1) {
                        return true;
                    } else {
                        // 変動係数が0.6以上でも最新企業価値が高ければOK
                        return cvb.getLatestCorporateValue().compareTo(cvb.getAverageCorporateValue()) > -1;
                    }
                })
                // 予想株価
                .filter(cvb -> {
                    if (Objects.nonNull(cvb.getForecastStock())) {
                        // 株価予想が存在する場合、最新株価より高ければOK
                        return isHigher(cvb.getForecastStock(), cvb.getLatestStockPrice());
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.toList()));
    }

    /**
     * 予想株価が最新株価より高い and 予想株価と最新株価の差が100以上 であること
     *
     * @param forecast 予想株価
     * @param latest   最新株価
     * @return bool
     */
    private boolean isHigher(final BigDecimal forecast, final BigDecimal latest) {
        return (forecast.divide(latest, 3, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(1.1)) > 0) &&
                (forecast.subtract(latest).compareTo(BigDecimal.valueOf(100)) > 0);
    }

    /**
     * 企業価値等を算出してすべてを表示する
     *
     * @return 会社一覧
     */
    @NewSpan("ViewService.corporateViewAll")
    public List<CorporateViewBean> corporateViewAll() {
        return sortedCompanyList(getCorporateViewBeanList());
    }

    /**
     * 企業価値等を割安度でソートする
     *
     * @return ソート後のリスト
     */
    @NewSpan("ViewService.sortByDiscountRate")
    public List<CorporateViewBean> sortByDiscountRate() {
        return corporateView().stream()
                .sorted(Comparator.comparing(CorporateViewBean::getDiscountRate).reversed())
                .collect(Collectors.toList());
    }

    private List<CorporateViewBean> getCorporateViewBeanList() {
        return corporateViewDao.selectAll().stream()
                // 提出日が存在したら表示する
                .filter(corporateViewBean -> corporateViewBean.getSubmitDate() != null)
                .collect(Collectors.toList());
    }

    private List<CorporateViewBean> sortedCompanyList(final List<CorporateViewBean> viewBeanList) {
        return viewBeanList.stream()
                .sorted(Comparator
                        .comparing(CorporateViewBean::getSubmitDate).reversed()
                        .thenComparing(CorporateViewBean::getCode))
                .collect(Collectors.toList());
    }

    /**
     * 非同期で表示するリストをアップデートする
     */
    @NewSpan("ViewService.updateCorporateView")
    @Async
    @Transactional
    public void updateCorporateView() {
        final var beanAllList = corporateViewDao.selectAll();
        companyAllTargeted().stream()
                .map(corporateViewLogic::corporateViewOf)
                .forEach(corporateViewBean -> {
                    final var match = beanAllList.stream()
                            .map(CorporateViewBean::getCode)
                            .anyMatch(corporateViewBean.getCode()::equals);
                    if (match) {
                        corporateViewDao.update(corporateViewBean);
                    } else {
                        corporateViewDao.insert(corporateViewBean);
                    }
                });
        slackProxy.sendMessage("g.c.i.f.domain.service.ViewService.display.update.complete.corporate");

        FundanalyzerLogClient.logService(
                "表示アップデートが正常に終了しました。",
                Category.VIEW,
                Process.UPDATE
        );
    }

    // ----------

    /**
     * 会社情報の更新日を取得する
     *
     * @return 最新更新日
     */
    @NewSpan("ViewService.companyUpdated")
    public String companyUpdated() {
        return companyDao.selectAll().stream()
                .map(Company::getUpdatedAt)
                .max(LocalDateTime::compareTo)
                .map(dateTime -> dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .orElse("null");
    }

    // ----------

    /**
     * 処理結果をSlackに通知する
     *
     * @param submitDate 対象提出日
     */
    @NewSpan("ViewService.notice")
    public CompletableFuture<Void> notice(final LocalDate submitDate) {
        final var documentList = documentDao.selectByTypeAndSubmitDate("120", submitDate);
        groupBySubmitDate(documentList).forEach(el -> {
            if (el.getCountTarget().equals(el.getCountScraped()) &&
                    el.getCountTarget().equals(el.getCountAnalyzed())) {
                // info message
                slackProxy.sendMessage(
                        "g.c.i.f.domain.service.ViewService.processing.notice.info",
                        el.getSubmitDate(), el.getCountTarget());
            } else {
                // warn message
                slackProxy.sendMessage(
                        "g.c.i.f.domain.service.ViewService.processing.notice.warn",
                        el.getSubmitDate(), el.getCountTarget(), el.getCountNotScraped());
            }
        });
        return null;
    }

    // ----------

    /**
     * 処理状況を表示するためのリストを取得する
     *
     * @return 処理状況リスト
     */
    @NewSpan("ViewService.edinetListview")
    public List<EdinetListViewBean> edinetListview() {
        final var viewBeanList = edinetListViewDao.selectAll();
        viewBeanList.removeIf(
                el -> el.getCountTarget().equals(el.getCountScraped()) &&
                        el.getCountTarget().equals(el.getCountAnalyzed())
        );
        return sortedEdinetList(viewBeanList);
    }

    /**
     * すべての処理状況を表示するためのリストを取得する
     *
     * @return 処理状況リスト
     */
    @NewSpan("ViewService.edinetListViewAll")
    public List<EdinetListViewBean> edinetListViewAll() {
        return sortedEdinetList(edinetListViewDao.selectAll());
    }

    private List<EdinetListViewBean> sortedEdinetList(final List<EdinetListViewBean> viewBeanList) {
        return viewBeanList.stream()
                .sorted(Comparator.comparing(EdinetListViewBean::getSubmitDate).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 非同期で表示する処理状況リストをアップデートする
     *
     * @param documentTypeCode 書類種別コード
     */
    @NewSpan("ViewService.updateEdinetListView")
    @Async
    @Transactional
    public void updateEdinetListView(final String documentTypeCode) {
        final var beanAllList = edinetListViewDao.selectAll();
        final var documentList = documentDao.selectByDocumentTypeCode(documentTypeCode);
        groupBySubmitDate(documentList)
                .forEach(edinetListViewBean -> {
                    final var match = beanAllList.stream()
                            .map(EdinetListViewBean::getSubmitDate)
                            .anyMatch(edinetListViewBean.getSubmitDate()::equals);
                    if (match) {
                        edinetListViewDao.update(edinetListViewBean);
                    } else {
                        edinetListViewDao.insert(edinetListViewBean);
                    }
                });

        slackProxy.sendMessage("g.c.i.f.domain.service.ViewService.display.update.complete.edinet.list");

        FundanalyzerLogClient.logService(
                "処理状況アップデートが正常に終了しました。",
                Category.VIEW,
                Process.UPDATE
        );
    }

    /**
     * 対象提出日の処理状況をアップデートする
     *
     * @param documentTypeCode 書類種別コード
     * @param submitDate       対象提出日
     */
    @NewSpan("ViewService.updateEdinetListView")
    @Transactional
    public void updateEdinetListView(final String documentTypeCode, final LocalDate submitDate) {
        final var documentList = documentDao.selectByTypeAndSubmitDate(documentTypeCode, submitDate);
        groupBySubmitDate(documentList).forEach(edinetListViewDao::update);

        FundanalyzerLogClient.logService(
                MessageFormat.format("処理状況アップデートが正常に終了しました。対象提出日:{0}", submitDate),
                Category.VIEW,
                Process.UPDATE
        );
    }

    // ----------

    /**
     * 企業ごとの銘柄詳細情報を取得する
     *
     * @param code 会社コード
     * @return 銘柄詳細情報
     */
    public BrandDetailViewBean brandDetailView(final String code) {
        return new BrandDetailViewBean(
                brandDetailCorporateViewLogic.brandDetailCompanyViewOf(code),
                corporateViewDao.selectByCode(code.substring(0, 4)),
                analysisResultDao.selectByCompanyCode(code).stream()
                        .sorted(Comparator.comparing(AnalysisResult::getPeriod).reversed())
                        .collect(Collectors.toList()),
                brandDetailCorporateViewLogic.brandDetailFinancialStatement(code),
                stockPriceDao.selectByCode(code).stream()
                        .map(StockPrice::ofBrandDetail)
                        .distinct()
                        .sorted(Comparator.comparing(StockPrice::getTargetDate).reversed())
                        .collect(Collectors.toList()),
                minkabuDao.selectByCode(code)
        );
    }

    // ----------

    /**
     * 提出日ごとの処理詳細情報を取得する
     *
     * @param submitDate 対象提出日
     * @return スクレイピング処理詳細情報
     */
    public EdinetDetailViewBean edinetDetailView(final LocalDate submitDate) {
        return edinetDetailViewLogic.edinetDetailView("120", submitDate, companyAllTargeted());
    }

    // ----------

    private List<EdinetListViewBean> groupBySubmitDate(final List<Document> documentList) {
        return documentList.stream()
                // 提出日ごとに件数をカウントする
                .collect(Collectors.groupingBy(Document::getSubmitDate, Collectors.counting()))
                // map -> stream
                .entrySet()
                .stream().map(submitDateCountAllEntry -> edinetListViewLogic.counter(
                        submitDateCountAllEntry.getKey(),
                        submitDateCountAllEntry.getValue(),
                        documentList,
                        companyAllTargeted()
                )).collect(Collectors.toList());
    }

    private List<Company> companyAllTargeted() {
        final var companyList = companyDao.selectAll();
        final var bank = industryDao.selectByName("銀行業");
        final var insurance = industryDao.selectByName("保険業");

        return companyList.stream()
                .filter(company -> company.getCode().isPresent())
                // 銀行業、保険業は対象外とする
                .filter(company -> !bank.getId().equals(company.getIndustryId()))
                .filter(company -> !insurance.getId().equals(company.getIndustryId()))
                .collect(Collectors.toList());
    }
}
