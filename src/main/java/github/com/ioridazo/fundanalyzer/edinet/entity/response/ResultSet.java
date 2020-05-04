package github.com.ioridazo.fundanalyzer.edinet.entity.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultSet {

    // 件数
    @JsonProperty("count")
    private String count;
}