package com.magampick.geocode.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 위치정보요약DB 1회 적재기 (ADR-002, 주기 갱신 없음). {@code geocode-load} 프로파일에서만 활성 — 평상시 부팅/CI/테스트에는 영향 없다.
 *
 * <p>실행: {@code --spring.profiles.active=<env>,geocode-load
 * --geocode.load.dir="...\202604_위치정보요약DB_전체분"}. CP949 '|' 파일을 스트림 파싱({@link GeocodeLineParser})하고,
 * 좌표는 적재 SQL 에서 {@code ST_Transform(5179→4326)} 로 변환해 GEOGRAPHY 로 저장한다. PK 충돌(완전중복 행)은 {@code ON
 * CONFLICT DO NOTHING} 으로 흡수 → 재실행 안전.
 */
@Slf4j
@Component
@Profile("geocode-load")
public class GeocodeDataLoader implements CommandLineRunner {

  private static final Charset CP949 = Charset.forName("MS949");
  private static final int BATCH_SIZE = 2000;
  private static final String INSERT_SQL =
      "INSERT INTO geocode_buildings"
          + " (road_name_code, underground, building_main_no, building_sub_no, road_address,"
          + " location)"
          + " VALUES (?, ?, ?, ?, ?, ST_Transform(ST_SetSRID(ST_MakePoint(?, ?), 5179), 4326))"
          + " ON CONFLICT DO NOTHING";

  private final JdbcTemplate jdbcTemplate;
  private final String dir;
  private final List<String> files;

  public GeocodeDataLoader(
      JdbcTemplate jdbcTemplate,
      @Value("${geocode.load.dir}") String dir,
      @Value("${geocode.load.files:entrc_seoul.txt,entrc_gyunggi.txt}") List<String> files) {
    this.jdbcTemplate = jdbcTemplate;
    this.dir = dir;
    this.files = files;
  }

  @Override
  public void run(String... args) throws Exception {
    log.info("지오코딩 적재 시작. dir={}, files={}", dir, files);
    long processed = 0;
    for (String file : files) {
      processed += loadFile(Path.of(dir, file));
    }
    long count = jdbcTemplate.queryForObject("SELECT count(*) FROM geocode_buildings", Long.class);
    log.info("지오코딩 적재 완료. 처리 {} 행, 테이블 보유 {} 행.", processed, count);
  }

  private long loadFile(Path path) throws IOException {
    long processed = 0;
    long skipped = 0;
    List<GeocodeRow> batch = new ArrayList<>(BATCH_SIZE);
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(Files.newInputStream(path), CP949))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Optional<GeocodeRow> row = GeocodeLineParser.parse(line);
        if (row.isEmpty()) {
          skipped++;
          continue;
        }
        batch.add(row.get());
        processed++;
        if (batch.size() >= BATCH_SIZE) {
          flush(batch);
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        flush(batch);
      }
    }
    log.info("파일 적재 완료. file={}, processed={}, skipped={}", path.getFileName(), processed, skipped);
    return processed;
  }

  private void flush(List<GeocodeRow> batch) {
    jdbcTemplate.batchUpdate(
        INSERT_SQL,
        batch,
        batch.size(),
        (ps, row) -> {
          ps.setString(1, row.roadNameCode());
          ps.setBoolean(2, row.underground());
          ps.setInt(3, row.buildingMainNo());
          ps.setInt(4, row.buildingSubNo());
          ps.setString(5, row.roadAddress());
          ps.setDouble(6, row.x());
          ps.setDouble(7, row.y());
        });
  }
}
