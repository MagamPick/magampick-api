-- Phase 11: 고객센터 도메인 — inquiries / faqs 생성
-- 의존: 선행 마이그레이션 없음 (독립 테이블)

CREATE TABLE inquiries
(
    id             BIGSERIAL    PRIMARY KEY,
    author_role    VARCHAR(20)  NOT NULL,
    author_id      BIGINT       NOT NULL,
    category       VARCHAR(20)  NOT NULL,
    title          VARCHAR(40)  NOT NULL,
    content        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    answer_content TEXT,
    answered_at    TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inquiries_author_role CHECK (author_role IN ('CUSTOMER', 'SELLER')),
    CONSTRAINT chk_inquiries_category CHECK (
        category IN ('PAYMENT','ORDER','COUPON','ACCOUNT','REPORT','SETTLEMENT','STORE','PRODUCT','ETC')
    ),
    CONSTRAINT chk_inquiries_status CHECK (status IN ('PENDING', 'ANSWERED'))
);

-- 본인 문의 목록 조회 + 최신순
CREATE INDEX idx_inquiries_author ON inquiries (author_role, author_id, created_at DESC);
-- 관리자 상태 필터
CREATE INDEX idx_inquiries_status ON inquiries (status);

CREATE TABLE faqs
(
    id         BIGSERIAL    PRIMARY KEY,
    audience   VARCHAR(20)  NOT NULL,
    question   VARCHAR(200) NOT NULL,
    answer     TEXT         NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_faqs_audience CHECK (audience IN ('CUSTOMER', 'SELLER'))
);

-- audience 별 sort_order 정렬 조회
CREATE INDEX idx_faqs_audience_sort ON faqs (audience, sort_order);

-- ───────────────────────────────────────────────────────────────────────────────
-- seed: FAQ 12건 (소비자 6 + 사장 6)
-- ───────────────────────────────────────────────────────────────────────────────

-- 소비자 FAQ (audience = CUSTOMER), sort_order 0~5
INSERT INTO faqs (audience, question, answer, sort_order)
VALUES
    ('CUSTOMER', '픽업은 어떻게 하나요?',
     '결제 완료 후 받은 4자리 픽업 코드를 매장 직원에게 보여주시면 바로 받을 수 있어요.',
     0),
    ('CUSTOMER', '픽업 시간을 지나치면 어떻게 되나요?',
     '픽업 시간이 지나도 주문과 픽업 코드는 그대로 유지돼요. 매장 영업 중에 방문해 코드를 보여주시면 받을 수 있어요. 다만 끝내 찾아가지 못한 주문은 환불이 제한될 수 있으니, 픽업이 어려우면 미리 매장에 연락해 주세요.',
     1),
    ('CUSTOMER', '쿠폰은 어떻게 사용하나요?',
     '결제 페이지의 "혜택 적용 — 쿠폰" 항목에서 보유한 쿠폰 중 하나를 선택할 수 있어요. 최소 결제 금액 5,000원 이상부터 사용 가능합니다.',
     2),
    ('CUSTOMER', '포인트는 어떻게 적립되나요?',
     '결제 시 결제 금액의 1~3%가 자동 적립되며, 리뷰 작성 시 추가 보너스가 지급돼요. 1P = 1원으로 결제 시 사용할 수 있어요.',
     3),
    ('CUSTOMER', '환불이 가능한가요?',
     '픽업 전이라면 매장에 직접 연락해 환불 가능 여부를 확인해 주세요. 매장마다 정책이 다를 수 있습니다.',
     4),
    ('CUSTOMER', '단골 매장은 어떻게 등록하나요?',
     '매장 상세 화면 우상단의 ♡ 아이콘을 누르면 단골 가게로 등록되며, 새 마감 할인 알림을 우선적으로 받을 수 있어요.',
     5);

-- 사장 FAQ (audience = SELLER), sort_order 0~5
INSERT INTO faqs (audience, question, answer, sort_order)
VALUES
    ('SELLER', '정산은 언제 이루어지나요?',
     '매출 정산은 매월 정해진 회차에 일괄 진행되며, 정산 내역은 마이 > 정산 내역에서 확인할 수 있어요. 정산 계좌는 매장 정보 수정에서 변경할 수 있습니다.',
     0),
    ('SELLER', '마감 할인 상품은 어떻게 등록하나요?',
     '홈 또는 상품 화면에서 [마감 할인 등록]을 눌러 정가·할인가·마감 시각·수량을 입력하면 등록돼요. 등록 즉시 고객 앱에 노출됩니다.',
     1),
    ('SELLER', '수수료는 어떻게 부과되나요?',
     '결제 완료된 주문 금액에 한해 수수료가 부과되며, 정산 시 자동 차감됩니다. 자세한 요율은 마이 > 정산 내역의 수수료 안내를 참고해 주세요.',
     2),
    ('SELLER', '주문을 거절할 수 있나요?',
     '재고 소진 등 부득이한 경우 주문 상세에서 거절 사유를 선택해 거절할 수 있어요. 거절 시 고객에게 자동 안내되고 결제 금액은 환불됩니다.',
     3),
    ('SELLER', '영업시간·임시 휴무는 어디서 설정하나요?',
     '마이 > 매장 관리 > 영업시간에서 요일별 영업시간과 임시 휴무를 설정할 수 있어요. 휴무로 설정하면 해당 기간 동안 고객 노출이 중단됩니다.',
     4),
    ('SELLER', '리뷰에 답글을 달 수 있나요?',
     '마이 > 리뷰 관리에서 고객 리뷰를 확인하고 답글을 작성할 수 있어요. 정성스러운 답글은 매장 신뢰도를 높여줍니다.',
     5);
