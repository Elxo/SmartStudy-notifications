package com.calendar.university;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class TuribaUniversity implements University {

    private static final String BASE_URL = "https://nodarbibas.turiba.lv";
    private static final String INDEX_URL = BASE_URL + "/";

    @Override
    public String getName() { return "Turiba University"; }

    @Override
    public String getCode() { return "TURIBA"; }

    @Override
    public List<String> fetchGroups() throws IOException {
        Document doc = Jsoup.connect(INDEX_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(15_000)
                .get();

        // All group links have href containing "Grupa="
        Elements links = doc.select("a[href*=Grupa=]");

        return links.stream()
                .map(a -> a.text().trim())
                .filter(g -> !g.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public String buildScheduleUrl(String groupCode, int weekOffset) {
        return BASE_URL + "/pub_nod.asp?Grupa=" + groupCode + "&ned=" + weekOffset;
    }
}
