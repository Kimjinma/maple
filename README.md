# 메이플스토리 장비 추천 시뮬레이터 (Maple Item Recommender)

> **"가진 자본(메소)으로 어떤 아이템부터 바꾸는 게 가장 스펙업이 많이 될까?"**  
> 유저들의 실제 고민에서 출발한 가성비 최적화 장비 추천 백엔드 서비스입니다.

단순히 좋은 아이템을 나열하는 것을 넘어, **배낭 알고리즘(Knapsack Problem)**을 응용해 (상승 스탯 / 소모 비용) 효율이 가장 높은 최적의 아이템 조합을 계산합니다. **Nexon Open API**를 연동하여 유저 캐릭터의 현재 스펙과 착용 장비를 정밀하게 분석하고 시뮬레이션합니다.

## 프로젝트 정보
* **진행 기간:** 2025.12.15 ~ 2026.02.25
* **참여 인원:** 1인 (개인 프로젝트)
* **GitHub:** [https://github.com/Kimjinma/maple](https://github.com/Kimjinma/maple)
* **Demo URL:** [http://54.180.24.126:8080/calculator](http://54.180.24.126:8080/calculator)

---

## 🛠 Skills & Tech Stack
* **Backend:** `Java 17`, `Spring Boot 3`, `Spring Data JPA`
* **Database & Cache:** `MariaDB`, `Spring Cache (@Cacheable)`
* **Infra/DevOps:** `AWS EC2`, `Docker`
* **External API/Tool:** `Nexon Open API`, `Swagger`

---

## 핵심 로직 및 문제 해결 (Core Logic & Troubleshooting)

<details>
<summary><b>1. 내 캐릭터 기준 '스탯 가치' 동적 계산</b></summary>
<div markdown="1">


* **데이터 파싱:** 넥슨 API를 호출하여 전투력, 주스탯, 보스 데미지 등 100여 가지 스펙 데이터를 파싱해 객체화합니다.
* **가치 환산 로직:** 유저마다 '주스탯 1%'나 '공격력 1'이 올려주는 실제 전투력 상승치가 다릅니다. 이 문제를 해결하기 위해, 유저의 현재 스펙을 기준으로 서로 다른 옵션들을 동일한 지표(환산 점수)로 비교·변환할 수 있는 정밀한 계산 로직을 직접 구현했습니다.
</div>
</details>

<details>
<summary><b>2. 가성비 기반 장비 추천 알고리즘 설계 (Knapsack Problem 적용)</b></summary>
<div markdown="1">

* **우선순위 선별:** 장착 가능한 장비 데이터(DB) 중 가격 대비 점수 상승량이 가장 높은 아이템을 우선적으로 선별합니다.
* **알고리즘 고도화 (Local Maxima 극복):** 정해진 예산 내에서 최적해를 찾다가 특정 상황에서 효율 계산이 멈춰버리는 현상(Local Maxima)을 발견했습니다. 이를 뚫어내기 위해, 알고리즘 연산 후반부에 특정 조건에서 강제로 상위 장비를 탐색해 고점을 갱신하는 **강제 업그레이드(Forced Upgrade) 휴리스틱 예외 로직**을 추가하여 추천 결과의 정확도를 대폭 높였습니다.
</div>
</details>

<details>
<summary><b>3. 외부 API 의존성 극복 및 시스템 안정성 방어</b></summary>
<div markdown="1">

* **인메모리 캐싱 도입:** 넥슨 API를 매번 실시간으로 호출하면 넥슨 서버의 속도 제한(Rate Limit)에 걸리거나 페이지 로딩 병목이 발생하는 문제가 있었습니다. `Spring @Cacheable` 계층을 도입하여 불필요한 중복 호출을 줄이고, 캐시 히트 시 응답 속도를 O(1) 수준으로 대폭 개선했습니다.
* **전역 예외 처리:** 외부 서버(Nexon) 장애 상황 시, 내 서버 전체가 뻗지 않고 우아하게 실패(Graceful Failure)하도록 `@RestControllerAdvice` 기반 Global Exception Handler를 구성해 가용성을 높였습니다.
</div>
</details>

---

## 성능 및 확장성을 고려한 아키텍처 고민 (Architecture)

<details>
<summary><b>대용량 연산 최적화를 위한 의도적 역정규화(Denormalization)</b></summary>
<div markdown="1">

* **고민 및 해결:** 추천 알고리즘의 특성상, 서버가 수많은 장비 매물의 스펙(공/마, 스탯, 잠재 등)을 인메모리로 한 번에 불러와 무거운 조합 연산을 수행해야 합니다.  
* 이때 발생하는 Database JOIN 오버헤드와 I/O 병목을 원천 차단하기 위해, **장비 테이블을 단일 와이드 테이블(Wide-Column)로 의도적으로 역정규화**하여 조회 성능을 극대화했습니다.
</div>
</details>

<details>
<summary><b>확장성을 고려한 스키마 설계 및 향후 개선 목표 (OCP 달성)</b></summary>
<div markdown="1">

* **무보엠(무기/보조/엠블렘) 확장 대비:** 현재 시뮬레이션은 방어구와 장신구 위주로 구축되어 있으나, 핵심 스펙인 `공격력 %` 및 `마력 %` 컬럼을 선제적으로 DB 스키마와 엔티티에 촘촘히 설계해 두었습니다. 향후 아키텍처 구조의 대규도 변경 없이 무보엠 추천 로직을 즉각 붙일 수 있는 확장성을 확보했습니다.
* **비즈니스 로직 리팩토링 목표:** 현재 서비스 레이어에 잔존하는 `contains("파프니르")`와 같은 문자열 파싱과 하드코딩 분기(if-else)를, 향후 서버 기동 시 정적 `Enum`과 `Map`을 이용해 메모리에 구조화하는 방식으로 개편할 예정입니다. 신규 세트 장비가 추가되더라도 핵심 내부 로직은 수정할 필요가 없는 **개방-폐쇄 원칙(OCP)** 달성을 목표로 고도화 중입니다.
</div>
</details>

---


##  배포 및 실행 방법 (How to run)

AWS EC2 환경에서 **Docker**를 이용해 구동 및 배포합니다.
*(로컬 실행 시 `application.yml`에 Nexon API Key 및 DB 정보 설정이 필요합니다.)*


```bash
# 1. 프로젝트 빌드
./gradlew clean build -x test

# 2. Docker 이미지 빌드
docker build -t maple-recommender .

# 3. 컨테이너 백그라운드 실행 (포트 8080)
docker run -d -p 8080:8080 maple-recommender
