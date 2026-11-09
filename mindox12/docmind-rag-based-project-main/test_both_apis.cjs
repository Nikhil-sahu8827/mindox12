const fs = require('fs');

async function testEndpoints() {
  try {
    // Test auth login to get a real token
    const loginRes = await fetch('http://localhost:8081/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usernameOrEmail: 'admin@docmind.com', password: 'Password@123' })
    });
    
    let token = '';
    if (loginRes.ok) {
      const loginData = await loginRes.json();
      token = loginData.data?.token || '';
      console.log("Logged in successfully! Token length:", token.length);
    } else {
      console.log("Login failed with status:", loginRes.status);
    }

    // Now test /api/v1/study/dashboard
    const studyRes = await fetch('http://localhost:8081/api/v1/study/dashboard', {
      headers: { Authorization: `Bearer ${token}` }
    });
    console.log("Study Dashboard Status:", studyRes.status);
    if (studyRes.ok) {
      const studyData = await studyRes.json();
      console.log("Study Data Success:", studyData.success);
      console.log("Today Hours:", studyData.data?.todayHours);
      console.log("Courses count:", studyData.data?.courses?.length);
    }

    // Now test /api/v1/growth/dashboard
    const growthRes = await fetch('http://localhost:8081/api/v1/growth/dashboard', {
      headers: { Authorization: `Bearer ${token}` }
    });
    console.log("Growth Dashboard Status:", growthRes.status);
    if (growthRes.ok) {
      const growthData = await growthRes.json();
      console.log("Growth Data Success:", growthData.success);
      console.log("Total Topics:", growthData.data?.totalTopicsCount);
    }
  } catch (err) {
    console.error("Test failed:", err);
  }
}

testEndpoints();
