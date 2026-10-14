param([string]$Base='http://127.0.0.1:8100')
$ErrorActionPreference='Stop'
function Login($account){$r=Invoke-RestMethod "$Base/api/demo/login" -Method Post -ContentType application/json -Body (@{account=$account}|ConvertTo-Json);return @{Authorization="Bearer $($r.token)"}}
function Call($path,$headers,$method='GET',$body=$null){$args=@{Uri="$Base/api$path";Headers=$headers;Method=$method};if($null-ne $body){$args.ContentType='application/json; charset=utf-8';$args.Body=$body|ConvertTo-Json}; $result=Invoke-RestMethod @args;foreach($item in $result){Write-Output $item}}
function Check($condition,$message){if(!$condition){throw "FAIL: $message"};Write-Output "PASS: $message"}
function Await($predicate,$description){for($i=0;$i-lt 30;$i++){if(&$predicate){Write-Output "PASS: $description";return};Start-Sleep -Seconds 2};throw "Timeout: $description"}
$customer=Login 'customer-a';$operator=Login 'operator-a';$owner=Login 'owner-a';$other=Login 'customer-b'
$health=Call '/system' $owner
Check (($health.services|Where-Object status -ne 'UP').Count-eq 0) '四个业务服务均可访问'
$body=@{requestKey=[guid]::NewGuid().ToString();origin='苏州验收仓';destination='上海验收仓';cargo='验收配件';weightKg=120;distanceKm=110}
$order=Call '/shipments' $customer 'POST' $body
$again=Call '/shipments' $customer 'POST' $body
Check ($order.id-eq $again.id) '重复下单返回相同运单'
Check (@(Call '/shipments' $other|Where-Object id -eq $order.id).Count-eq 0) '商家之间运单隔离'
Await { (Call "/shipments/$($order.id)" $customer).state-eq 'ASSIGNED' } '异步调度完成'
$null=Call "/shipments/$($order.id)/pickup" $operator 'POST' @{}
$null=Call "/shipments/$($order.id)/deliver" $operator 'POST' @{}
$null=Call "/shipments/$($order.id)/sign" $customer 'POST' @{signature='演示验收人'}
Await { @(Call '/invoices' $customer|Where-Object shipment_id -eq $order.id).Count-eq 1 } '签收后生成唯一账单'
$invoice=Call '/invoices' $customer|Where-Object shipment_id -eq $order.id
$null=Call "/invoices/$($invoice.id)/pay" $customer 'POST' @{}
$paid=Call "/invoices/$($invoice.id)/pay" $customer 'POST' @{}
Check ($paid.state-eq 'PAID') '重复模拟支付保持已支付状态'
Await { @(Call ("/tracking/"+$order.id) $customer).Count-eq 5 } '五个轨迹版本完整'
Await { @(Call '/assignments' $owner|Where-Object { $_.shipment_id-eq $order.id -and $_.state-eq 'RELEASED' }).Count-eq 1 } '签收后运力释放'
Write-Output "CargoFlow 核心流程验收完成。运单：$($order.id)"
