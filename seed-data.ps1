########################################################
# SEED DATA - Insurance System (Fixed)
########################################################

$BASE = "http://localhost:8080"
$headers = @{ "Content-Type" = "application/json" }

Write-Host "`n>>> Dang nhap ADMIN..." -ForegroundColor Cyan
$loginBody = '{"email":"dungphdse@gmail.com","role":"ROLE_ADMIN","fullName":"Admin He Thong"}'

try {
    $loginRes = Invoke-RestMethod -Uri "$BASE/auth/login" -Method POST -Headers $headers -Body $loginBody
    $token = $loginRes.data.token
    $authHeaders = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $token" }
    Write-Host "  [OK] Dang nhap thanh cong!" -ForegroundColor Green
} catch {
    Write-Host "  [ERR] Dang nhap that bai: $_" -ForegroundColor Red
    exit 1
}

# Policy 1 - DRAFT
$p1 = '{
  "policyNumber": "POL-2026-001",
  "insured": {
    "name": "Nguyen Van An",
    "type": "INDIVIDUAL",
    "email": "an.nguyen@example.com",
    "phone": "0901234567",
    "address": "123 Nguyen Trai, Q1, TP.HCM"
  },
  "locations": [{
    "locationId": "LOC-001",
    "address": "123 Nguyen Trai, Q1, TP.HCM",
    "description": "Van phong chinh",
    "coverages": [{
      "coverageCode": "FIRE",
      "coverageType": "Property",
      "coverageLimit": 500000000,
      "deductible": 5000000,
      "premium": 2500000
    }]
  }],
  "effectiveDate": "2026-01-01T00:00:00Z",
  "expirationDate": "2027-01-01T00:00:00Z"
}'

# Policy 2
$p2 = '{
  "policyNumber": "POL-2026-002",
  "insured": {
    "name": "Tran Thi Bich",
    "type": "BUSINESS",
    "email": "bich.tran@example.com",
    "phone": "0912345678",
    "address": "456 Le Loi, Q3, TP.HCM"
  },
  "locations": [{
    "locationId": "LOC-002",
    "address": "456 Le Loi, Q3, TP.HCM",
    "description": "Kho hang",
    "coverages": [
      {"coverageCode": "THEFT","coverageType": "Property","coverageLimit": 200000000,"deductible": 2000000,"premium": 1800000},
      {"coverageCode": "FIRE","coverageType": "Property","coverageLimit": 300000000,"deductible": 3000000,"premium": 1500000}
    ]
  }],
  "effectiveDate": "2026-02-01T00:00:00Z",
  "expirationDate": "2027-02-01T00:00:00Z"
}'

# Policy 3
$p3 = '{
  "policyNumber": "POL-2026-003",
  "insured": {
    "name": "Le Minh Duc",
    "type": "BUSINESS",
    "email": "duc.le@example.com",
    "phone": "0923456789",
    "address": "789 CMT8, Q10, TP.HCM"
  },
  "locations": [{
    "locationId": "LOC-003",
    "address": "789 CMT8, Q10, TP.HCM",
    "description": "Showroom xe hoi",
    "coverages": [{
      "coverageCode": "LIABILITY",
      "coverageType": "Liability",
      "coverageLimit": 1000000000,
      "deductible": 10000000,
      "premium": 5000000
    }]
  }],
  "effectiveDate": "2026-03-01T00:00:00Z",
  "expirationDate": "2027-03-01T00:00:00Z"
}'

# Policy 4
$p4 = '{
  "policyNumber": "POL-2026-004",
  "insured": {
    "name": "Pham Thi Huong",
    "type": "INDIVIDUAL",
    "email": "huong.pham@example.com",
    "phone": "0934567890",
    "address": "321 Hai Ba Trung, Q1, TP.HCM"
  },
  "locations": [{
    "locationId": "LOC-004",
    "address": "321 Hai Ba Trung, Q1, TP.HCM",
    "description": "Nha o rieng",
    "coverages": [
      {"coverageCode": "FIRE","coverageType": "Property","coverageLimit": 800000000,"deductible": 8000000,"premium": 4000000},
      {"coverageCode": "FLOOD","coverageType": "Property","coverageLimit": 200000000,"deductible": 2000000,"premium": 1200000}
    ]
  }],
  "effectiveDate": "2026-01-15T00:00:00Z",
  "expirationDate": "2027-01-15T00:00:00Z"
}'

# Policy 5
$p5 = '{
  "policyNumber": "POL-2026-005",
  "insured": {
    "name": "Hoang Van Cuong",
    "type": "BUSINESS",
    "email": "cuong.hoang@example.com",
    "phone": "0945678901",
    "address": "654 Dien Bien Phu, Q3, TP.HCM"
  },
  "locations": [{
    "locationId": "LOC-005",
    "address": "654 Dien Bien Phu, Q3, TP.HCM",
    "description": "Nha may san xuat",
    "coverages": [{
      "coverageCode": "MACHINERY",
      "coverageType": "Property",
      "coverageLimit": 2000000000,
      "deductible": 20000000,
      "premium": 10000000
    }]
  }],
  "effectiveDate": "2026-04-01T00:00:00Z",
  "expirationDate": "2027-04-01T00:00:00Z"
}'

$allPolicies = @(
    @{num="POL-2026-001"; body=$p1},
    @{num="POL-2026-002"; body=$p2},
    @{num="POL-2026-003"; body=$p3},
    @{num="POL-2026-004"; body=$p4},
    @{num="POL-2026-005"; body=$p5}
)

Write-Host "`n>>> Tao 5 Policy mau..." -ForegroundColor Cyan

$created = 0
$skipped = 0
foreach ($item in $allPolicies) {
    try {
        $res = Invoke-RestMethod -Uri "$BASE/policies" -Method POST -Headers $authHeaders -Body $item.body
        Write-Host "  [OK] Tao: $($item.num)" -ForegroundColor Green
        $created++
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 409) {
            Write-Host "  [SKIP] $($item.num) da ton tai" -ForegroundColor Yellow
            $skipped++
        } else {
            Write-Host "  [ERR] $($item.num): $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

Write-Host "`n=============================" -ForegroundColor White
Write-Host " Tao moi : $created  |  Bo qua: $skipped" -ForegroundColor Green
Write-Host " Mo trinh duyet: http://localhost:5173" -ForegroundColor Cyan
Write-Host "=============================" -ForegroundColor White
