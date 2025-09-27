(function () {
  // Hôm nay (tiếng Việt)
  const elToday = document.getElementById('todayVi');
  if (elToday) {
    const d = new Date();
    const thang = ['Một','Hai','Ba','Tư','Năm','Sáu','Bảy','Tám','Chín','Mười Một','Mười Hai'];
    const dd = String(d.getDate()).padStart(2, '0');
    elToday.textContent = dd + ' Tháng ' + thang[d.getMonth()] + ' ' + d.getFullYear();
  }

  // Back-to-top
  const btn = document.getElementById('btnBackToTop');
  function toggleBtn(){ btn && (btn.style.display = window.scrollY > 200 ? 'inline-flex' : 'none'); }
  window.addEventListener('scroll', toggleBtn, { passive: true });
  toggleBtn();

  window.topFunction = function () { window.scrollTo({ top: 0, behavior: 'smooth' }); };
})();