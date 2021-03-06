package github.com.ioridazo.fundanalyzer.web.scheduler;

import github.com.ioridazo.fundanalyzer.client.slack.SlackClient;
import github.com.ioridazo.fundanalyzer.domain.domain.specification.DocumentSpecification;
import github.com.ioridazo.fundanalyzer.domain.service.AnalysisService;
import github.com.ioridazo.fundanalyzer.exception.FundanalyzerRuntimeException;
import github.com.ioridazo.fundanalyzer.web.model.DateInputData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockSchedulerTest {

    private AnalysisService analysisService;
    private DocumentSpecification documentSpecification;
    private SlackClient slackClient;

    private StockScheduler scheduler;

    @BeforeEach
    void setUp() {
        this.analysisService = Mockito.mock(AnalysisService.class);
        this.documentSpecification = Mockito.mock(DocumentSpecification.class);
        this.slackClient = Mockito.mock(SlackClient.class);

        this.scheduler = Mockito.spy(new StockScheduler(analysisService, documentSpecification, slackClient));
        scheduler.hourOfStock = 13;
    }

    @Nested
    class stockScheduler {

        @DisplayName("stockScheduler : 日が一致する提出日の会社の株価を更新する")
        @Test
        void insertStockScheduler_ok() {
            doReturn(LocalDateTime.of(2021, 5, 29, 13, 0)).when(scheduler).nowLocalDateTime();

            doReturn(LocalDate.parse("2021-02-06")).when(scheduler).nowLocalDate();
            when(documentSpecification.stockSchedulerTargetList("6")).thenReturn(List.of(
                    LocalDate.parse("2021-01-06"),
                    LocalDate.parse("2021-02-06")
            ));

            assertDoesNotThrow(() -> scheduler.stockScheduler());
            verify(analysisService, times(1)).importStock(DateInputData.of(LocalDate.parse("2021-01-06")));
            verify(analysisService, times(1)).importStock(DateInputData.of(LocalDate.parse("2021-02-06")));
            verify(slackClient, times(1)).sendMessage("g.c.i.f.web.scheduler.notice.info", 2);
        }

        @DisplayName("stockScheduler : 想定外のエラーが発生したときはSlack通知する")
        @Test
        void insertStockScheduler_throwable() {
            doReturn(LocalDateTime.of(2021, 5, 29, 13, 0)).when(scheduler).nowLocalDateTime();

            doReturn(LocalDate.parse("2021-02-06")).when(scheduler).nowLocalDate();
            when(documentSpecification.stockSchedulerTargetList("6")).thenReturn(List.of(
                    LocalDate.parse("2021-02-06")
            ));
            doThrow(new FundanalyzerRuntimeException()).when(analysisService).importStock((DateInputData) any());

            assertThrows(FundanalyzerRuntimeException.class, () -> scheduler.stockScheduler());
            verify(slackClient, times(1)).sendMessage(eq("g.c.i.f.web.scheduler.notice.error"), any());
        }

        @DisplayName("stockScheduler : 株価を削除する")
        @Test
        void deleteStockScheduler_ok() {
            doReturn(LocalDateTime.of(2021, 5, 29, 13, 0)).when(scheduler).nowLocalDateTime();

            doReturn(LocalDate.parse("2021-02-06")).when(scheduler).nowLocalDate();
            when(analysisService.deleteStock()).thenReturn(1);

            assertDoesNotThrow(() -> scheduler.stockScheduler());
            verify(analysisService, times(1)).deleteStock();
            verify(slackClient, times(1)).sendMessage("g.c.i.f.web.scheduler.notice.info", 1);
        }

        @DisplayName("stockScheduler : 想定外のエラーが発生したときはSlack通知する")
        @Test
        void deleteStockScheduler_throwable() {
            doReturn(LocalDateTime.of(2021, 5, 29, 13, 0)).when(scheduler).nowLocalDateTime();

            doReturn(LocalDate.parse("2021-02-06")).when(scheduler).nowLocalDate();
            doThrow(new FundanalyzerRuntimeException()).when(analysisService).deleteStock();

            assertThrows(FundanalyzerRuntimeException.class, () -> scheduler.stockScheduler());
            verify(slackClient, times(1)).sendMessage(eq("g.c.i.f.web.scheduler.notice.error"), any());
        }

        @DisplayName("stockScheduler : 処理時間外")
        @Test
        void stockScheduler_noTarget() {
            doReturn(LocalDateTime.of(2021, 5, 29, 15, 0)).when(scheduler).nowLocalDateTime();

            assertDoesNotThrow(() -> scheduler.stockScheduler());
            verify(analysisService, times(0)).importStock((DateInputData) any());
            verify(analysisService, times(0)).deleteStock();
        }
    }
}