package com.magampick.analytics.domain;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Spring MVC @RequestParam 소문자 바인딩용 Converter. WebMvc 가 @Component 자동 등록. */
@Component
public class AnalyticsPeriodConverter implements Converter<String, AnalyticsPeriod> {

  @Override
  public AnalyticsPeriod convert(String source) {
    return AnalyticsPeriod.from(source);
  }
}
